package com.shiwu.ai.anthropic;

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
import java.util.stream.Stream;

@Slf4j
public class AnthropicChatModel implements ChatModel {

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

    public AnthropicChatModel(String fullUrl, String model, int maxTokens, double temperature, String apiKey) {
        this.fullUrl = fullUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.apiKey = apiKey;

        this.httpVersion = resolveHttpVersion(fullUrl);
        log.info("Anthropic model init -> URL: {}, model: {}, HTTP version: {}", fullUrl, model, httpVersion);

        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(httpVersion)
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
        List<AnthropicChatDto.Tool> apiTools = buildApiTools(toolCallbacks);

        AnthropicChatDto.Request req = buildRequest(messages, apiTools, false);

        try {
            log.info("Anthropic call -> URL: {}, model: {}, stream: false, tools: {}",
                    fullUrl, model, apiTools.size());

            AnthropicChatDto.Response resp = sendRequest(req);

            if (resp == null || CollectionUtils.isEmpty(resp.getContent())) {
                return emptyResponse();
            }

            return handleResponse(resp, messages, apiTools, toolCallbacks);

        } catch (RuntimeException e) {
            log.error("Anthropic call failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Anthropic call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Anthropic 服务调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return Flux.just(emptyResponse());
        }

        List<ToolCallback> toolCallbacks = resolveToolCallbacks(prompt);
        List<AnthropicChatDto.Tool> apiTools = buildApiTools(toolCallbacks);

        AnthropicChatDto.Request req = buildRequest(messages, apiTools, true);

        log.info("Anthropic stream -> URL: {}, model: {}, stream: true, enableThinking: {}, tools: {}",
                fullUrl, model, getEffectiveThinking(), apiTools.size());

        try {
            String requestBody = objectMapper.writeValueAsString(req);
            log.info("[SDK] Anthropic请求体大小: {} 字符, httpVersion={}", requestBody.length(), httpVersion);

            long sdkStartTime = System.currentTimeMillis();

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Accept", "text/event-stream")
                    .version(httpVersion)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            HttpRequest httpRequest = requestBuilder.build();
            log.info("[SDK] Anthropic发送异步请求 -> URL: {}, version: {}", fullUrl, httpVersion);

            final boolean[] sdkFirstToken = {false};
            final boolean[] thinkingDetected = {false};
            final long[] thinkingStartTime = {0};
            final int[] thinkingChunkCount = {0};

            CompletableFuture<HttpResponse<Stream<String>>> future =
                    httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines());

            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    log.error("[SDK] Anthropic sendAsync失败，耗时: {}ms, 错误: {}", System.currentTimeMillis() - sdkStartTime, throwable.getMessage(), throwable);
                } else {
                    log.info("[SDK] Anthropic sendAsync完成，状态: {}，耗时: {}ms", response.statusCode(), System.currentTimeMillis() - sdkStartTime);
                }
            });

            return Mono.fromFuture(future)
            .flatMapMany(response -> {
                log.info("[SDK] Anthropic HTTP连接建立，状态: {}，耗时: {}ms", response.statusCode(), System.currentTimeMillis() - sdkStartTime);
                return Flux.fromStream(response.body());
            })
            .filter(line -> line != null && !line.trim().isEmpty())
            .filter(line -> line.trim().startsWith("data:"))
            .map(line -> line.trim().substring(5).trim())
            .filter(payload -> !"[DONE]".equals(payload))
            .flatMap(payload -> {
                try {
                    AnthropicChatDto.StreamEvent event = objectMapper.readValue(payload, AnthropicChatDto.StreamEvent.class);
                    String type = event.getType();

                    if ("content_block_start".equals(type) && event.getContentBlock() != null) {
                        if ("thinking".equals(event.getContentBlock().getType())) {
                            if (!thinkingDetected[0]) {
                                thinkingDetected[0] = true;
                                thinkingStartTime[0] = System.currentTimeMillis();
                                log.warn("[SDK] ⚠️ Anthropic检测到思考模式(thinking block)！enableThinking={} 但模型仍在思考，这会导致延迟！", getEffectiveThinking());
                            }
                            return Flux.empty();
                        }
                    }

                    if ("content_block_delta".equals(type) && event.getDelta() != null) {
                        if ("thinking_delta".equals(event.getDelta().getType())) {
                            thinkingChunkCount[0]++;
                            if (thinkingChunkCount[0] % 20 == 0) {
                                log.info("[SDK] Anthropic思考阶段进行中，已收到 {} 个思考chunk，耗时: {}ms", thinkingChunkCount[0], System.currentTimeMillis() - thinkingStartTime[0]);
                            }
                            return Flux.empty();
                        }

                        String text = event.getDelta().getText();
                        if (text != null && !text.isEmpty()) {
                            if (!sdkFirstToken[0]) {
                                sdkFirstToken[0] = true;
                                if (thinkingDetected[0]) {
                                    log.warn("[SDK] ⚠️ Anthropic思考阶段结束，思考耗时: {}ms ({}个chunk)，首token总耗时: {}ms",
                                            System.currentTimeMillis() - thinkingStartTime[0], thinkingChunkCount[0],
                                            System.currentTimeMillis() - sdkStartTime);
                                } else {
                                    log.info("[SDK] Anthropic首个有效token到达，耗时: {}ms", System.currentTimeMillis() - sdkStartTime);
                                }
                            }
                            Generation generation = new Generation(new AssistantMessage(text));
                            return Flux.just(new ChatResponse(Collections.singletonList(generation)));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Anthropic stream event: {}", e.getMessage());
                }
                return Flux.empty();
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> {
                log.error("Anthropic stream error: {}", error.getMessage(), error);
                Generation generation = new Generation(new AssistantMessage(""));
                return Flux.just(new ChatResponse(Collections.singletonList(generation)));
            });

        } catch (Exception e) {
            log.error("Anthropic stream init failed: {}", e.getMessage(), e);
            return Flux.just(emptyResponse());
        }
    }

    private ChatResponse handleResponse(AnthropicChatDto.Response resp,
                                         List<Message> originalMessages,
                                         List<AnthropicChatDto.Tool> apiTools,
                                         List<ToolCallback> toolCallbacks) {
        String stopReason = resp.getStopReason();

        if (!"tool_use".equals(stopReason)) {
            String content = extractTextContent(resp.getContent());
            Generation generation = new Generation(new AssistantMessage(content));
            return new ChatResponse(Collections.singletonList(generation));
        }

        return executeToolCallLoop(resp, originalMessages, apiTools, toolCallbacks);
    }

    private ChatResponse executeToolCallLoop(AnthropicChatDto.Response resp,
                                              List<Message> originalMessages,
                                              List<AnthropicChatDto.Tool> apiTools,
                                              List<ToolCallback> toolCallbacks) {
        List<AnthropicChatDto.Message> currentApiMessages = convertMessages(originalMessages);

        for (int round = 0; round < MAX_TOOL_CALL_ROUNDS; round++) {
            String stopReason = resp.getStopReason();

            if (!"tool_use".equals(stopReason)) {
                String content = extractTextContent(resp.getContent());
                Generation generation = new Generation(new AssistantMessage(content));
                return new ChatResponse(Collections.singletonList(generation));
            }

            List<AnthropicChatDto.ContentBlock> toolUseBlocks = extractToolUseBlocks(resp.getContent());
            if (CollectionUtils.isEmpty(toolUseBlocks)) {
                String content = extractTextContent(resp.getContent());
                Generation generation = new Generation(new AssistantMessage(content));
                return new ChatResponse(Collections.singletonList(generation));
            }

            List<AnthropicChatDto.ContentBlock> assistantContent = resp.getContent();
            AnthropicChatDto.Message assistantApiMsg = new AnthropicChatDto.Message("assistant", assistantContent);
            currentApiMessages.add(assistantApiMsg);

            Map<String, ToolCallback> callbackMap = new HashMap<>();
            for (ToolCallback tc : toolCallbacks) {
                callbackMap.put(tc.getToolDefinition().name(), tc);
            }

            List<AnthropicChatDto.ToolResultContent> toolResults = new ArrayList<>();
            for (AnthropicChatDto.ContentBlock toolUseBlock : toolUseBlocks) {
                String toolName = toolUseBlock.getName();
                String toolUseId = toolUseBlock.getId();
                Object toolInput = toolUseBlock.getInput();
                String toolArgsJson;
                try {
                    toolArgsJson = objectMapper.writeValueAsString(toolInput);
                } catch (Exception e) {
                    toolArgsJson = "{}";
                }

                ToolCallback callback = callbackMap.get(toolName);
                String toolResult;
                if (callback != null) {
                    toolResult = executeToolWithRetry(callback, toolName, toolArgsJson);
                } else {
                    toolResult = "Tool not found: " + toolName;
                }

                toolResults.add(new AnthropicChatDto.ToolResultContent(toolUseId, toolResult));
            }

            AnthropicChatDto.Message userToolResultMsg = new AnthropicChatDto.Message("user", toolResults);
            currentApiMessages.add(userToolResultMsg);

            AnthropicChatDto.Request nextReq = buildRequestFromApiMessages(currentApiMessages, apiTools, false);

            try {
                resp = sendRequest(nextReq);
            } catch (Exception e) {
                throw new RuntimeException("Anthropic 服务调用失败 (tool call round " + (round + 1) + "): " + e.getMessage(), e);
            }

            if (resp == null || CollectionUtils.isEmpty(resp.getContent())) {
                return emptyResponse();
            }
        }

        log.warn("Tool call loop reached max rounds ({})", MAX_TOOL_CALL_ROUNDS);
        return emptyResponse();
    }

    private AnthropicChatDto.Response sendRequest(AnthropicChatDto.Request req) throws Exception {
        String requestBody = objectMapper.writeValueAsString(req);

        Exception lastException = null;
        for (int attempt = 0; attempt <= HTTP_MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = HTTP_RETRY_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("[HTTP-Retry] Anthropic第{}次重试，等待{}ms", attempt, delay);
                    Thread.sleep(delay);
                }

                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .version(httpVersion)
                        .timeout(timeout)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody));

                HttpResponse<String> httpResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int statusCode = httpResponse.statusCode();
                String rawBody = httpResponse.body();

                if (statusCode == 429 || statusCode == 502 || statusCode == 503) {
                    lastException = new RuntimeException("Anthropic 服务调用失败 (HTTP " + statusCode + "): " + rawBody);
                    log.warn("[HTTP-Retry] Anthropic可重试的HTTP错误: status={}, attempt={}", statusCode, attempt);
                    continue;
                }

                if (statusCode >= 400) {
                    log.error("Anthropic API error: status={}, body={}", statusCode, rawBody);
                    throw new RuntimeException("Anthropic 服务调用失败 (HTTP " + statusCode + "): " + rawBody);
                }

                if (rawBody == null || rawBody.isEmpty()) {
                    return null;
                }

                return objectMapper.readValue(rawBody, AnthropicChatDto.Response.class);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("[HTTP-Retry] Anthropic请求失败: attempt={}, error={}", attempt, e.getMessage());
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Anthropic HTTP请求失败");
    }

    private String executeToolWithRetry(ToolCallback callback, String toolName, String toolArgs) {
        int maxRetries = 1;
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = 500L * attempt;
                    log.warn("[Tool-Retry] Anthropic工具{}第{}次重试，等待{}ms", toolName, attempt, delay);
                    Thread.sleep(delay);
                }
                return callback.call(toolArgs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return "Tool execution interrupted: " + toolName;
            } catch (Exception e) {
                lastException = e;
                log.error("Anthropic tool execution failed: name={}, attempt={}, error={}", toolName, attempt, e.getMessage(), e);
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

    private List<AnthropicChatDto.Tool> buildApiTools(List<ToolCallback> toolCallbacks) {
        if (CollectionUtils.isEmpty(toolCallbacks)) {
            return Collections.emptyList();
        }
        List<AnthropicChatDto.Tool> tools = new ArrayList<>();
        for (ToolCallback tc : toolCallbacks) {
            String name = tc.getToolDefinition().name();
            String description = tc.getToolDefinition().description();
            String inputSchema = tc.getToolDefinition().inputSchema();

            Map<String, Object> schema = parseSchema(inputSchema);
            tools.add(new AnthropicChatDto.Tool(name, description, schema));
        }
        return tools;
    }

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
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");
            fallback.put("properties", new LinkedHashMap<>());
            return fallback;
        }
    }

    private AnthropicChatDto.Request buildRequest(List<Message> messages,
                                                   List<AnthropicChatDto.Tool> apiTools,
                                                   boolean stream) {
        AnthropicChatDto.Request req = new AnthropicChatDto.Request();
        req.setModel(model);
        req.setMaxTokens(maxTokens);
        req.setStream(stream);

        if (temperature >= 0) {
            req.setTemperature(temperature);
        }

        String systemPrompt = null;
        List<AnthropicChatDto.Message> apiMessages = new ArrayList<>();

        for (Message m : messages) {
            if (m instanceof ToolResponseMessage) {
                ToolResponseMessage trm = (ToolResponseMessage) m;
                List<AnthropicChatDto.ToolResultContent> toolResults = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    toolResults.add(new AnthropicChatDto.ToolResultContent(tr.id(), tr.responseData()));
                }
                apiMessages.add(new AnthropicChatDto.Message("user", toolResults));
            } else if (m instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) m;
                if (am.hasToolCalls()) {
                    List<AnthropicChatDto.ContentBlock> contentBlocks = new ArrayList<>();
                    if (am.getText() != null && !am.getText().isEmpty()) {
                        AnthropicChatDto.ContentBlock textBlock = new AnthropicChatDto.ContentBlock();
                        textBlock.setType("text");
                        textBlock.setText(am.getText());
                        contentBlocks.add(textBlock);
                    }
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        AnthropicChatDto.ContentBlock toolUseBlock = new AnthropicChatDto.ContentBlock();
                        toolUseBlock.setType("tool_use");
                        toolUseBlock.setId(tc.id());
                        toolUseBlock.setName(tc.name());
                        try {
                            Object inputObj = objectMapper.readValue(tc.arguments(), new TypeReference<Object>() {});
                            toolUseBlock.setInput(inputObj);
                        } catch (Exception e) {
                            Map<String, Object> emptyInput = new LinkedHashMap<>();
                            toolUseBlock.setInput(emptyInput);
                        }
                        contentBlocks.add(toolUseBlock);
                    }
                    apiMessages.add(new AnthropicChatDto.Message("assistant", contentBlocks));
                } else {
                    apiMessages.add(new AnthropicChatDto.Message("assistant", am.getText()));
                }
            } else if (m instanceof UserMessage) {
                UserMessage um = (UserMessage) m;
                List<Media> mediaList = um.getMedia();
                if (!CollectionUtils.isEmpty(mediaList)) {
                    List<AnthropicChatDto.ContentBlock> contentBlocks = new ArrayList<>();
                    for (Media media : mediaList) {
                        AnthropicChatDto.ContentBlock imageBlock = buildImageContentBlock(media);
                        if (imageBlock != null) {
                            contentBlocks.add(imageBlock);
                        }
                    }
                    if (um.getText() != null && !um.getText().isEmpty()) {
                        AnthropicChatDto.ContentBlock textBlock = new AnthropicChatDto.ContentBlock();
                        textBlock.setType("text");
                        textBlock.setText(um.getText());
                        contentBlocks.add(textBlock);
                    }
                    apiMessages.add(new AnthropicChatDto.Message("user", contentBlocks));
                } else {
                    apiMessages.add(new AnthropicChatDto.Message("user", um.getText()));
                }
            } else if (m instanceof SystemMessage) {
                systemPrompt = m.getText();
            } else {
                String role = m.getMessageType().getValue();
                if ("system".equals(role)) {
                    systemPrompt = m.getText();
                } else {
                    apiMessages.add(new AnthropicChatDto.Message(role, m.getText()));
                }
            }
        }

        req.setMessages(apiMessages);
        if (systemPrompt != null) {
            req.setSystem(systemPrompt);
        }
        if (!CollectionUtils.isEmpty(apiTools)) {
            req.setTools(apiTools);
            Map<String, Object> toolChoice = new LinkedHashMap<>();
            toolChoice.put("type", "auto");
            req.setToolChoice(toolChoice);
        }

        // 请求级 thinking 优先于实例级配置
        Boolean effectiveThinking = getEffectiveThinking();
        if (effectiveThinking != null) {
            if (effectiveThinking) {
                Map<String, Object> thinking = new LinkedHashMap<>();
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", maxTokens);
                req.setThinking(thinking);
            } else {
                Map<String, Object> thinking = new LinkedHashMap<>();
                thinking.put("type", "disabled");
                req.setThinking(thinking);
            }
        }

        return req;
    }

    private AnthropicChatDto.Request buildRequestFromApiMessages(List<AnthropicChatDto.Message> apiMessages,
                                                                  List<AnthropicChatDto.Tool> apiTools,
                                                                  boolean stream) {
        AnthropicChatDto.Request req = new AnthropicChatDto.Request();
        req.setModel(model);
        req.setMaxTokens(maxTokens);
        req.setStream(stream);

        if (temperature >= 0) {
            req.setTemperature(temperature);
        }

        req.setMessages(apiMessages);
        if (!CollectionUtils.isEmpty(apiTools)) {
            req.setTools(apiTools);
            Map<String, Object> toolChoice = new LinkedHashMap<>();
            toolChoice.put("type", "auto");
            req.setToolChoice(toolChoice);
        }

        // 请求级 thinking 优先于实例级配置
        Boolean effectiveThinking = getEffectiveThinking();
        if (effectiveThinking != null) {
            if (effectiveThinking) {
                Map<String, Object> thinking = new LinkedHashMap<>();
                thinking.put("type", "enabled");
                thinking.put("budget_tokens", maxTokens);
                req.setThinking(thinking);
            } else {
                Map<String, Object> thinking = new LinkedHashMap<>();
                thinking.put("type", "disabled");
                req.setThinking(thinking);
            }
        }

        return req;
    }

    private List<AnthropicChatDto.Message> convertMessages(List<Message> messages) {
        String systemPrompt = null;
        List<AnthropicChatDto.Message> apiMessages = new ArrayList<>();

        for (Message m : messages) {
            if (m instanceof ToolResponseMessage) {
                ToolResponseMessage trm = (ToolResponseMessage) m;
                List<AnthropicChatDto.ToolResultContent> toolResults = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    toolResults.add(new AnthropicChatDto.ToolResultContent(tr.id(), tr.responseData()));
                }
                apiMessages.add(new AnthropicChatDto.Message("user", toolResults));
            } else if (m instanceof AssistantMessage) {
                AssistantMessage am = (AssistantMessage) m;
                if (am.hasToolCalls()) {
                    List<AnthropicChatDto.ContentBlock> contentBlocks = new ArrayList<>();
                    if (am.getText() != null && !am.getText().isEmpty()) {
                        AnthropicChatDto.ContentBlock textBlock = new AnthropicChatDto.ContentBlock();
                        textBlock.setType("text");
                        textBlock.setText(am.getText());
                        contentBlocks.add(textBlock);
                    }
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        AnthropicChatDto.ContentBlock toolUseBlock = new AnthropicChatDto.ContentBlock();
                        toolUseBlock.setType("tool_use");
                        toolUseBlock.setId(tc.id());
                        toolUseBlock.setName(tc.name());
                        try {
                            Object inputObj = objectMapper.readValue(tc.arguments(), new TypeReference<Object>() {});
                            toolUseBlock.setInput(inputObj);
                        } catch (Exception e) {
                            Map<String, Object> emptyInput = new LinkedHashMap<>();
                            toolUseBlock.setInput(emptyInput);
                        }
                        contentBlocks.add(toolUseBlock);
                    }
                    apiMessages.add(new AnthropicChatDto.Message("assistant", contentBlocks));
                } else {
                    apiMessages.add(new AnthropicChatDto.Message("assistant", am.getText()));
                }
            } else if (m instanceof UserMessage) {
                UserMessage um = (UserMessage) m;
                List<Media> mediaList = um.getMedia();
                if (!CollectionUtils.isEmpty(mediaList)) {
                    List<AnthropicChatDto.ContentBlock> contentBlocks = new ArrayList<>();
                    for (Media media : mediaList) {
                        AnthropicChatDto.ContentBlock imageBlock = buildImageContentBlock(media);
                        if (imageBlock != null) {
                            contentBlocks.add(imageBlock);
                        }
                    }
                    if (um.getText() != null && !um.getText().isEmpty()) {
                        AnthropicChatDto.ContentBlock textBlock = new AnthropicChatDto.ContentBlock();
                        textBlock.setType("text");
                        textBlock.setText(um.getText());
                        contentBlocks.add(textBlock);
                    }
                    apiMessages.add(new AnthropicChatDto.Message("user", contentBlocks));
                } else {
                    apiMessages.add(new AnthropicChatDto.Message("user", um.getText()));
                }
            } else if (m instanceof SystemMessage) {
                systemPrompt = m.getText();
            } else {
                String role = m.getMessageType().getValue();
                if ("system".equals(role)) {
                    systemPrompt = m.getText();
                } else {
                    apiMessages.add(new AnthropicChatDto.Message(role, m.getText()));
                }
            }
        }

        return apiMessages;
    }

    private AnthropicChatDto.ContentBlock buildImageContentBlock(Media media) {
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

            AnthropicChatDto.ContentBlock block = new AnthropicChatDto.ContentBlock();
            block.setType("image");
            AnthropicChatDto.ImageSource source = new AnthropicChatDto.ImageSource();
            source.setType("base64");
            source.setMediaType(mimeTypeStr);
            source.setData(base64Data);
            block.setSource(source);
            return block;
        } catch (Exception e) {
            log.error("Failed to build image content block: {}", e.getMessage(), e);
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

    private String extractTextContent(List<AnthropicChatDto.ContentBlock> contentBlocks) {
        StringBuilder sb = new StringBuilder();
        for (AnthropicChatDto.ContentBlock block : contentBlocks) {
            if ("text".equals(block.getType()) && block.getText() != null) {
                sb.append(block.getText());
            }
        }
        return sb.toString();
    }

    private List<AnthropicChatDto.ContentBlock> extractToolUseBlocks(List<AnthropicChatDto.ContentBlock> contentBlocks) {
        List<AnthropicChatDto.ContentBlock> toolUseBlocks = new ArrayList<>();
        for (AnthropicChatDto.ContentBlock block : contentBlocks) {
            if ("tool_use".equals(block.getType())) {
                toolUseBlocks.add(block);
            }
        }
        return toolUseBlocks;
    }

    private ChatResponse emptyResponse() {
        return new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(""))));
    }
}
