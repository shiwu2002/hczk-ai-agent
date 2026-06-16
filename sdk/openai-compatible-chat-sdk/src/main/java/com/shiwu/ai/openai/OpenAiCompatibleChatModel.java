package com.shiwu.ai.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
public class OpenAiCompatibleChatModel implements ChatModel {

    private static final int MAX_TOOL_CALL_ROUNDS = 10;
    private static final int HTTP_MAX_RETRIES = 2;
    private static final long HTTP_RETRY_BACKOFF_MS = 1000;

    private final String fullUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final String apiKey;
    private volatile Boolean enableThinking;
    /** 请求级 thinking 覆盖（优先于实例级 enableThinking），线程安全 */
    private static final ThreadLocal<Boolean> REQUEST_THINKING = new ThreadLocal<>();
    private final Duration timeout = Duration.ofSeconds(120);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final HttpClient.Version httpVersion;

    public OpenAiCompatibleChatModel(String fullUrl, String model, int maxTokens, double temperature, String apiKey) {
        this.fullUrl = fullUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.apiKey = apiKey;

        this.httpVersion = resolveHttpVersion(fullUrl);
        log.info("OpenAI compatible model init -> URL: {}, model: {}, HTTP version: {}", fullUrl, model, httpVersion);

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(httpVersion)
                .executor(Executors.newFixedThreadPool(4, r -> {
                    Thread t = new Thread(r, "openai-sdk-http");
                    t.setDaemon(true);
                    return t;
                }))
                .build();
    }

    private static HttpClient.Version resolveHttpVersion(String url) {
        try {
            URI uri = URI.create(url);
            int port = uri.getPort();
            if (port == 1234) {
                return HttpClient.Version.HTTP_1_1;
            }
        } catch (Exception ignored) {
        }
        return HttpClient.Version.HTTP_2;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    /** 设置当前请求的 thinking 参数（优先于实例级配置） */
    public static void setRequestThinking(Boolean thinking) {
        REQUEST_THINKING.set(thinking);
    }

    /** 清除当前请求的 thinking 参数 */
    public static void clearRequestThinking() {
        REQUEST_THINKING.remove();
    }

    /** 获取生效的 thinking 配置（请求级优先，其次实例级） */
    private Boolean getEffectiveThinking() {
        Boolean requestThinking = REQUEST_THINKING.get();
        return requestThinking != null ? requestThinking : this.enableThinking;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return emptyResponse();
        }

        List<ToolCallback> toolCallbacks = resolveToolCallbacks(prompt);
        List<OpenAiCompatibleChatDto.Tool> apiTools = buildApiTools(toolCallbacks);

        List<OpenAiCompatibleChatDto.Message> apiMessages = convertMessages(messages);

        OpenAiCompatibleChatDto.Request req = buildRequest(apiMessages, apiTools, false);

        try {
            log.info("OpenAI compatible call -> URL: {}, model: {}, stream: false, tools: {}",
                    fullUrl, model, apiTools.size());

            OpenAiCompatibleChatDto.Response resp = sendRequest(req);

            if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                log.warn("OpenAI compatible call returned null or empty choices");
                return emptyResponse();
            }

            return handleResponse(resp, apiMessages, apiTools, toolCallbacks);

        } catch (RuntimeException e) {
            log.error("OpenAI compatible call failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("OpenAI compatible call failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return Flux.just(emptyResponse());
        }

        List<ToolCallback> toolCallbacks = resolveToolCallbacks(prompt);
        List<OpenAiCompatibleChatDto.Tool> apiTools = buildApiTools(toolCallbacks);

        List<OpenAiCompatibleChatDto.Message> apiMessages = convertMessages(messages);
        OpenAiCompatibleChatDto.Request req = buildRequest(apiMessages, apiTools, true);

        log.info("OpenAI compatible stream -> URL: {}, model: {}, stream: true, enableThinking: {}, tools: {}",
                fullUrl, model, getEffectiveThinking(), apiTools.size());

        try {
            String requestBody = objectMapper.writeValueAsString(req);
            log.info("[SDK] 请求体大小: {} 字符, enable_thinking={}, httpVersion={}", requestBody.length(), req.getEnableThinking(), httpVersion);

            long sdkStartTime = System.currentTimeMillis();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .version(httpVersion)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpRequest httpRequest = requestBuilder.build();
            log.info("[SDK] 发送异步请求 -> URL: {}, version: {}", fullUrl, httpVersion);

            final boolean[] sdkFirstToken = {false};
            final boolean[] thinkingDetected = {false};
            final long[] thinkingStartTime = {0};
            final int[] thinkingChunkCount = {0};

            CompletableFuture<HttpResponse<Stream<String>>> future =
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines());

            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    log.error("[SDK] sendAsync失败，耗时: {}ms, 错误: {}", System.currentTimeMillis() - sdkStartTime, throwable.getMessage(), throwable);
                } else {
                    log.info("[SDK] sendAsync完成，状态: {}，耗时: {}ms", response.statusCode(), System.currentTimeMillis() - sdkStartTime);
                }
            });

            return Mono.fromFuture(future)
            .flatMapMany(response -> {
                int statusCode = response.statusCode();
                log.info("[SDK] HTTP连接建立，状态: {}，耗时: {}ms", statusCode, System.currentTimeMillis() - sdkStartTime);
                if (statusCode >= 400) {
                    // HTTP错误：收集完整响应体用于错误信息
                    StringBuilder errorBody = new StringBuilder();
                    try {
                        response.body().forEach(line -> errorBody.append(line));
                    } catch (Exception e) {
                        errorBody.append("(无法读取错误响应体: ").append(e.getMessage()).append(")");
                    }
                    String errorMsg = errorBody.length() > 0 ? errorBody.toString() : "(空响应体)";
                    log.error("[SDK] API返回HTTP错误: status={}, body={}", statusCode, errorMsg);
                    return Flux.error(new RuntimeException("AI 服务调用失败 (HTTP " + statusCode + "): " + errorMsg));
                }
                return Flux.fromStream(response.body());
            })
            .filter(line -> line != null && !line.trim().isEmpty())
            .filter(line -> line.trim().startsWith("data:"))
            .map(line -> line.trim().substring(5).trim())
            .filter(payload -> !"[DONE]".equals(payload))
            .flatMap(payload -> {
                try {
                    OpenAiCompatibleChatDto.Response chunk = objectMapper.readValue(payload, OpenAiCompatibleChatDto.Response.class);
                    if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                        OpenAiCompatibleChatDto.Choice choice = chunk.getChoices().get(0);
                        OpenAiCompatibleChatDto.ResponseMessage delta = choice.getDelta();

                        if (delta != null && delta.getReasoningContent() != null && !delta.getReasoningContent().isEmpty()) {
                            if (!thinkingDetected[0]) {
                                thinkingDetected[0] = true;
                                thinkingStartTime[0] = System.currentTimeMillis();
                                log.info("[SDK] 检测到思考模式(reasoning_content)，enableThinking={}", getEffectiveThinking());
                            }
                            thinkingChunkCount[0]++;
                            if (thinkingChunkCount[0] % 20 == 0) {
                                log.debug("[SDK] 思考阶段进行中，已收到 {} 个思考chunk，耗时: {}ms", thinkingChunkCount[0], System.currentTimeMillis() - thinkingStartTime[0]);
                            }
                            // 将思考内容通过metadata标记传递，不丢弃
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("reasoning", true);
                            AssistantMessage thinkingMessage = AssistantMessage.builder()
                                    .content(delta.getReasoningContent())
                                    .properties(metadata)
                                    .build();
                            Generation generation = new Generation(thinkingMessage);
                            return Flux.just(new ChatResponse(Collections.singletonList(generation)));
                        }

                        String content = extractContent(choice);
                        if (content != null && !content.isEmpty()) {
                            if (!sdkFirstToken[0]) {
                                sdkFirstToken[0] = true;
                                if (thinkingDetected[0]) {
                                    log.info("[SDK] 思考阶段结束，思考耗时: {}ms ({}个chunk)，首token总耗时: {}ms",
                                            System.currentTimeMillis() - thinkingStartTime[0], thinkingChunkCount[0],
                                            System.currentTimeMillis() - sdkStartTime);
                                } else {
                                    log.info("[SDK] 首个有效token到达，耗时: {}ms", System.currentTimeMillis() - sdkStartTime);
                                }
                            }
                            Generation generation = new Generation(new AssistantMessage(content));
                            return Flux.just(new ChatResponse(Collections.singletonList(generation)));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse OpenAI compatible stream event: {}", e.getMessage());
                }
                return Flux.empty();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> {
                log.error("OpenAI compatible stream error: {}", error.getMessage(), error);
                Generation generation = new Generation(new AssistantMessage(""));
                return Flux.just(new ChatResponse(Collections.singletonList(generation)));
            });

        } catch (Exception e) {
            log.error("OpenAI compatible stream init failed: {}", e.getMessage(), e);
            return Flux.just(emptyResponse());
        }
    }

    private ChatResponse handleResponse(OpenAiCompatibleChatDto.Response resp,
                                         List<OpenAiCompatibleChatDto.Message> apiMessages,
                                         List<OpenAiCompatibleChatDto.Tool> apiTools,
                                         List<ToolCallback> toolCallbacks) {
        OpenAiCompatibleChatDto.Choice choice = resp.getChoices().get(0);
        String finishReason = choice.getFinishReason();

        if (choice.getMessage() != null && !CollectionUtils.isEmpty(choice.getMessage().getToolCalls())) {
            for (OpenAiCompatibleChatDto.ToolCall tc : choice.getMessage().getToolCalls()) {
                log.info("Tool call requested: id={}, name={}, args={}",
                        tc.getId(), tc.getFunction().getName(), tc.getFunction().getArguments());
            }
        }

        if (!"tool_calls".equals(finishReason) || choice.getMessage() == null
                || CollectionUtils.isEmpty(choice.getMessage().getToolCalls())) {
            String content = extractContent(choice);
            Generation generation = new Generation(new AssistantMessage(content));
            return new ChatResponse(Collections.singletonList(generation));
        }

        return executeToolCallLoop(resp, apiMessages, apiTools, toolCallbacks);
    }

    private ChatResponse executeToolCallLoop(OpenAiCompatibleChatDto.Response resp,
                                              List<OpenAiCompatibleChatDto.Message> apiMessages,
                                              List<OpenAiCompatibleChatDto.Tool> apiTools,
                                              List<ToolCallback> toolCallbacks) {
        List<OpenAiCompatibleChatDto.Message> currentMessages = new ArrayList<>(apiMessages);

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            OpenAiCompatibleChatDto.Choice choice = resp.getChoices().get(0);
            String finishReason = choice.getFinishReason();

            if (!"tool_calls".equals(finishReason) || choice.getMessage() == null
                    || CollectionUtils.isEmpty(choice.getMessage().getToolCalls())) {
                String content = extractContent(choice);
                Generation generation = new Generation(new AssistantMessage(content));
                return new ChatResponse(Collections.singletonList(generation));
            }

            OpenAiCompatibleChatDto.ResponseMessage assistantMsg = choice.getMessage();
            OpenAiCompatibleChatDto.Message assistantApiMsg = new OpenAiCompatibleChatDto.Message("assistant", assistantMsg.getContent());
            assistantApiMsg.setToolCalls(assistantMsg.getToolCalls());
            currentMessages.add(assistantApiMsg);

            List<OpenAiCompatibleChatDto.ToolCall> toolCalls = assistantMsg.getToolCalls();
            log.info("Tool call round {}: model requests {} tool call(s)", round + 1, toolCalls.size());

            Map<String, ToolCallback> callbackMap = new HashMap<>();
            for (ToolCallback tc : toolCallbacks) {
                callbackMap.put(tc.getToolDefinition().name(), tc);
            }

            for (OpenAiCompatibleChatDto.ToolCall toolCall : toolCalls) {
                String toolName = toolCall.getFunction().getName();
                String toolArgs = toolCall.getFunction().getArguments();
                String toolCallId = toolCall.getId();

                ToolCallback callback = callbackMap.get(toolName);
                String toolResult;
                if (callback != null) {
                    toolResult = executeToolWithRetry(callback, toolName, toolArgs);
                } else {
                    toolResult = "Tool not found: " + toolName;
                }

                OpenAiCompatibleChatDto.Message toolMsg = new OpenAiCompatibleChatDto.Message("tool", toolResult);
                toolMsg.setToolCallId(toolCallId);
                toolMsg.setName(toolName);
                currentMessages.add(toolMsg);
            }

            OpenAiCompatibleChatDto.Request nextReq = buildRequest(currentMessages, apiTools, false);

            try {
                resp = sendRequest(nextReq);
            } catch (Exception e) {
                throw new RuntimeException("AI 服务调用失败 (tool call round " + (round + 1) + "): " + e.getMessage(), e);
            }

            if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                return emptyResponse();
            }
        }

        log.warn("Tool call loop reached max rounds ({})", MAX_TOOL_CALL_ROUNDS);
        return emptyResponse();
    }

    private OpenAiCompatibleChatDto.Response sendRequest(OpenAiCompatibleChatDto.Request req) throws Exception {
        String requestBody = objectMapper.writeValueAsString(req);

        Exception lastException = null;
        for (int attempt = 0; attempt <= HTTP_MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = HTTP_RETRY_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("[HTTP-Retry] 第{}次重试，等待{}ms", attempt, delay);
                    Thread.sleep(delay);
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .version(httpVersion)
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpResponse<String> httpResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int statusCode = httpResponse.statusCode();
                String rawBody = httpResponse.body();

                if (statusCode == 429 || statusCode == 502 || statusCode == 503) {
                    lastException = new RuntimeException("AI 服务调用失败 (HTTP " + statusCode + "): " + rawBody);
                    log.warn("[HTTP-Retry] 可重试的HTTP错误: status={}, attempt={}", statusCode, attempt);
                    continue;
                }

                if (statusCode >= 400) {
                    log.error("OpenAI compatible API error: status={}, body={}", statusCode, rawBody);
                    throw new RuntimeException("AI 服务调用失败 (HTTP " + statusCode + "): " + rawBody);
                }

                if (rawBody == null || rawBody.isEmpty()) {
                    return null;
                }

                return objectMapper.readValue(rawBody, OpenAiCompatibleChatDto.Response.class);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("[HTTP-Retry] 请求失败: attempt={}, error={}", attempt, e.getMessage());
            }
        }

        throw lastException != null ? lastException : new RuntimeException("HTTP请求失败");
    }

    private String executeToolWithRetry(ToolCallback callback, String toolName, String toolArgs) {
        int maxRetries = 1;
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = 500L * attempt;
                    log.warn("[Tool-Retry] 工具{}第{}次重试，等待{}ms", toolName, attempt, delay);
                    Thread.sleep(delay);
                }
                return callback.call(toolArgs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "Tool execution interrupted: " + toolName;
            } catch (Exception e) {
                lastException = e;
                log.error("Tool execution failed: name={}, attempt={}, error={}", toolName, attempt, e.getMessage(), e);
            }
        }
        return "Tool execution error (" + (maxRetries + 1) + " attempts): " +
                (lastException != null ? lastException.getMessage() : "unknown");
    }

    private List<ToolCallback> resolveToolCallbacks(Prompt prompt) {
        ChatOptions options = prompt.getOptions();
        if (options == null) {
            return Collections.emptyList();
        }
        if (options instanceof ToolCallingChatOptions) {
            List<ToolCallback> callbacks = ((ToolCallingChatOptions) options).getToolCallbacks();
            if (!CollectionUtils.isEmpty(callbacks)) {
                return callbacks;
            }
        }
        return Collections.emptyList();
    }

    private List<OpenAiCompatibleChatDto.Tool> buildApiTools(List<ToolCallback> toolCallbacks) {
        if (CollectionUtils.isEmpty(toolCallbacks)) {
            return Collections.emptyList();
        }
        List<OpenAiCompatibleChatDto.Tool> tools = new ArrayList<>();
        for (ToolCallback tc : toolCallbacks) {
            String name = tc.getToolDefinition().name();
            String description = tc.getToolDefinition().description();
            String inputSchema = tc.getToolDefinition().inputSchema();

            Map<String, Object> parameters = parseSchema(inputSchema);
            OpenAiCompatibleChatDto.FunctionDefinition funcDef =
                    new OpenAiCompatibleChatDto.FunctionDefinition(name, description, parameters);
            tools.add(new OpenAiCompatibleChatDto.Tool("function", funcDef));
        }
        return tools;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "object");
            empty.put("properties", new LinkedHashMap<>());
            return empty;
        }
        try {
            Map<String, Object> schema = objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});
            schema.remove("$schema");
            schema.remove("additionalProperties");
            return schema;
        } catch (Exception e) {
            log.warn("Failed to parse tool schema JSON: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");
            fallback.put("properties", new LinkedHashMap<>());
            return fallback;
        }
    }

    private List<OpenAiCompatibleChatDto.Message> convertMessages(List<Message> messages) {
        List<OpenAiCompatibleChatDto.Message> apiMessages = new ArrayList<>();
        for (Message m : messages) {
            if (m instanceof ToolResponseMessage) {
                ToolResponseMessage trm = (ToolResponseMessage) m;
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    OpenAiCompatibleChatDto.Message toolMsg = new OpenAiCompatibleChatDto.Message("tool", tr.responseData());
                    toolMsg.setToolCallId(tr.id());
                    toolMsg.setName(tr.name());
                    apiMessages.add(toolMsg);
                }
            } else if (m instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) m;
                if (am.hasToolCalls()) {
                    OpenAiCompatibleChatDto.Message assistantMsg = new OpenAiCompatibleChatDto.Message("assistant", am.getText());
                    List<OpenAiCompatibleChatDto.ToolCall> apiToolCalls = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        apiToolCalls.add(new OpenAiCompatibleChatDto.ToolCall(
                                tc.id(), tc.type(),
                                new OpenAiCompatibleChatDto.FunctionCall(tc.name(), tc.arguments())
                        ));
                    }
                    assistantMsg.setToolCalls(apiToolCalls);
                    apiMessages.add(assistantMsg);
                } else {
                    String role;
                    if (m instanceof UserMessage) {
                        role = "user";
                    } else {
                        role = "assistant";
                    }
                    apiMessages.add(new OpenAiCompatibleChatDto.Message(role, m.getText()));
                }
            } else if (m instanceof UserMessage) {
                UserMessage um = (UserMessage) m;
                List<Media> mediaList = um.getMedia();
                if (!CollectionUtils.isEmpty(mediaList)) {
                    List<OpenAiCompatibleChatDto.ContentPart> parts = new ArrayList<>();
                    for (Media media : mediaList) {
                        OpenAiCompatibleChatDto.ContentPart imagePart = buildImageContentPart(media);
                        if (imagePart != null) {
                            parts.add(imagePart);
                        }
                    }
                    if (um.getText() != null && !um.getText().isEmpty()) {
                        parts.add(OpenAiCompatibleChatDto.ContentPart.text(um.getText()));
                    }
                    apiMessages.add(new OpenAiCompatibleChatDto.Message("user", parts));
                } else {
                    apiMessages.add(new OpenAiCompatibleChatDto.Message("user", um.getText()));
                }
            } else if (m instanceof SystemMessage) {
                apiMessages.add(new OpenAiCompatibleChatDto.Message("system", m.getText()));
            } else {
                String role = m.getMessageType().getValue();
                apiMessages.add(new OpenAiCompatibleChatDto.Message(role, m.getText()));
            }
        }
        return apiMessages;
    }

    private OpenAiCompatibleChatDto.ContentPart buildImageContentPart(Media media) {
        try {
            Object data = media.getData();
            byte[] imageBytes;
            if (data instanceof byte[]) {
                imageBytes = (byte[]) data;
            } else if (data instanceof String) {
                String dataStr = (String) data;
                if (dataStr.startsWith("data:")) {
                    int commaIdx = dataStr.indexOf(",");
                    if (commaIdx > 0) {
                        imageBytes = Base64.getDecoder().decode(dataStr.substring(commaIdx + 1));
                    } else {
                        log.warn("Invalid data URL format in Media");
                        return null;
                    }
                } else {
                    log.warn("Media data is String but not a data URL");
                    return null;
                }
            } else if (data instanceof java.net.URI) {
                imageBytes = downloadFromUri((java.net.URI) data);
                if (imageBytes == null) return null;
            } else {
                log.warn("Unsupported Media data type: {}", data != null ? data.getClass().getName() : "null");
                return null;
            }

            String mimeTypeStr = media.getMimeType() != null ? media.getMimeType().toString() : "image/jpeg";
            String base64Data = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeTypeStr + ";base64," + base64Data;

            return OpenAiCompatibleChatDto.ContentPart.image(dataUrl);
        } catch (Exception e) {
            log.error("Failed to build image content part: {}", e.getMessage(), e);
            return null;
        }
    }

    private byte[] downloadFromUri(java.net.URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            log.warn("Image download from URI failed: HTTP {} for {}", response.statusCode(), uri);
            return null;
        } catch (Exception e) {
            log.error("Image download from URI error for {}: {}", uri, e.getMessage());
            return null;
        }
    }

    private OpenAiCompatibleChatDto.Request buildRequest(List<OpenAiCompatibleChatDto.Message> apiMessages,
                                                          List<OpenAiCompatibleChatDto.Tool> apiTools,
                                                          boolean stream) {
        OpenAiCompatibleChatDto.Request req = new OpenAiCompatibleChatDto.Request();
        req.setModel(model);
        req.setMaxTokens(maxTokens);
        req.setTemperature(temperature);
        req.setStream(stream);
        req.setMessages(apiMessages);
        if (!CollectionUtils.isEmpty(apiTools)) {
            req.setTools(apiTools);
            req.setToolChoice("auto");
        }
        // 只在 enable_thinking=true 时发送该参数，避免不支持该参数的模型返回400错误
        Boolean effectiveThinking = getEffectiveThinking();
        if (Boolean.TRUE.equals(effectiveThinking)) {
            req.setEnableThinking(true);
        }
        return req;
    }

    private String extractContent(OpenAiCompatibleChatDto.Choice choice) {
        if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
            return choice.getMessage().getContent();
        }
        if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
            return choice.getDelta().getContent();
        }
        return "";
    }

    private ChatResponse emptyResponse() {
        return new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(""))));
    }
}
