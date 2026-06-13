package com.shiwu.ai.modelscope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SDK版本：基于 WebClient 的 ChatModel 实现，面向 OpenAI 兼容的 /v1/chat/completions。
 * 本类仅负责与 HTTP 服务交互；请求/响应 DTO 在 ModelScopeChatDto 中定义。
 *
 * 与业务工程解耦后，可通过本地依赖 com.shiwu.ai:modelscope-chat-sdk 使用。
 */
@Slf4j
public class ModelScopeWebClientChatModel implements ChatModel {

    private final WebClient webClient;
    private final String model;
    private final boolean streamEnabled;
    private final boolean enableSearch;
    private final Duration timeout = Duration.ofSeconds(30);
    private final ObjectMapper objectMapper;

    public ModelScopeWebClientChatModel(WebClient webClient, String model, boolean streamEnabled) {
        this(webClient, model, streamEnabled, true);
    }

    public ModelScopeWebClientChatModel(WebClient webClient, String model, boolean streamEnabled, boolean enableSearch) {
        this.webClient = webClient;
        this.model = model;
        this.streamEnabled = streamEnabled;
        this.enableSearch = enableSearch;
        // 配置 ObjectMapper 忽略 null 值，避免序列化不需要的参数
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return emptyResponse();
        }

        // 构造请求体
        ModelScopeChatDto.Request req = new ModelScopeChatDto.Request();
        req.setModel(model);
        req.setMessages(toDtoMessages(messages));
        
        // 非流式调用时必须设置stream=false或不设置
        req.setStream(false);
        
        // 设置默认参数以避免API错误
        req.setTemperature(0.8);
        req.setMax_tokens(1024);
        
        // ModelScope API要求：非流式调用时enable_thinking必须为false
        req.setEnable_thinking(false);
        
        // enable_search参数可选，根据配置决定是否启用
        if (enableSearch) {
            req.setEnable_search(true);
        }
        
        // 添加详细日志以诊断400错误
        try {
            String requestJson = objectMapper.writeValueAsString(req);
            log.info("【ModelScope请求】模型: {}, 流式: {}, 请求体: {}", model, streamEnabled, requestJson);
        } catch (Exception e) {
            log.warn("无法序列化请求体用于日志: {}", e.getMessage());
        }

        if (streamEnabled) {
            // 流式模式：按 SSE "data: {...}" 分片解析，聚合 delta.content
            Flux<String> flux = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(timeout);

            StringBuilder sb = new StringBuilder();
            flux.toIterable().forEach(line -> {
                String trimmed = line == null ? "" : line.trim();
                if (!trimmed.startsWith("data:")) {
                    return;
                }
                String payload = trimmed.substring(5).trim();
                if ("[DONE]".equals(payload)) {
                    return;
                }
                try {
                    ModelScopeChatDto.Response respChunk = objectMapper.readValue(payload, ModelScopeChatDto.Response.class);
                    if (respChunk.getChoices() != null && !respChunk.getChoices().isEmpty()) {
                        ModelScopeChatDto.Choice choice = respChunk.getChoices().get(0);
                        if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                            sb.append(choice.getDelta().getContent());
                        } else if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                            // 某些实现可能在最终块回传完整 message
                            sb.append(choice.getMessage().getContent());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 ModelScope 流式块失败: {}", e.getMessage());
                }
            });

            Generation generation = new Generation(new AssistantMessage(sb.toString()));
            return new ChatResponse(Collections.singletonList(generation));
        } else {
            // 非流模式：一次性 JSON 响应
            ModelScopeChatDto.Response resp = null;
            try {
                resp = webClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(req)
                        .retrieve()
                        .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                .doOnNext(errorBody -> log.error("【ModelScope错误响应】状态码: {}, 响应体: {}", 
                                    clientResponse.statusCode(), errorBody))
                                .then(clientResponse.createException())
                        )
                        .bodyToMono(ModelScopeChatDto.Response.class)
                        .timeout(timeout)
                        .block();
                
                log.info("【ModelScope响应成功】收到响应，choices数量: {}", 
                    resp != null && resp.getChoices() != null ? resp.getChoices().size() : 0);
            } catch (Exception e) {
                log.error("【ModelScope请求失败】异常: {}", e.getMessage(), e);
                throw e;
            }

            if (resp == null || CollectionUtils.isEmpty(resp.getChoices())) {
                log.warn("ModelScope 返回空响应或无 choices");
                return emptyResponse();
            }

            String content = "";
            ModelScopeChatDto.Choice choice = resp.getChoices().get(0);
            if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                content = choice.getMessage().getContent();
            } else if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                content = choice.getDelta().getContent();
            }

            Generation generation = new Generation(new AssistantMessage(safe(content)));
            return new ChatResponse(Collections.singletonList(generation));
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (CollectionUtils.isEmpty(messages)) {
            return Flux.just(emptyResponse());
        }

        // 构造请求体
        ModelScopeChatDto.Request req = new ModelScopeChatDto.Request();
        req.setModel(model);
        req.setMessages(toDtoMessages(messages));
        
        // 流式调用时必须设置stream=true
        req.setStream(true);
        
        // 设置默认参数
        req.setTemperature(0.8);
        req.setMax_tokens(1024);
        
        // ModelScope API要求：流式调用时enable_thinking必须为false
        req.setEnable_thinking(false);
        
        // enable_search参数可选，根据配置决定是否启用
        if (enableSearch) {
            req.setEnable_search(true);
        }
        
        // 添加详细日志
        try {
            String requestJson = objectMapper.writeValueAsString(req);
            log.info("【ModelScope流式请求】模型: {}, 请求体: {}", model, requestJson);
        } catch (Exception e) {
            log.warn("无法序列化请求体用于日志: {}", e.getMessage());
        }

        // 返回SSE流式响应的Flux
        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(req)
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    clientResponse -> clientResponse.bodyToMono(String.class)
                        .doOnNext(errorBody -> log.error("【ModelScope流式错误响应】状态码: {}, 响应体: {}", 
                            clientResponse.statusCode(), errorBody))
                        .then(clientResponse.createException())
                )
                .bodyToFlux(String.class)
                .timeout(timeout)
                .filter(line -> line != null && line.trim().startsWith("data:"))
                .map(line -> {
                    String trimmed = line.trim();
                    String payload = trimmed.substring(5).trim();
                                    
                    log.debug("【ModelScope 流式接收】原始数据：{}", payload);
                                    
                    // 处理 [DONE] 标记
                    if ("[DONE]".equals(payload)) {
                        log.debug("【ModelScope 流式接收】收到 DONE 标记");
                        return null;
                    }
                                    
                    try {
                        ModelScopeChatDto.Response respChunk = objectMapper.readValue(payload, ModelScopeChatDto.Response.class);
                        if (respChunk.getChoices() != null && !respChunk.getChoices().isEmpty()) {
                            ModelScopeChatDto.Choice choice = respChunk.getChoices().get(0);
                            String content = "";
                                            
                            if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                                content = choice.getDelta().getContent();
                                log.debug("【ModelScope 流式提取】delta.content: '{}'", content);
                            } else if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                                content = choice.getMessage().getContent();
                                log.debug("【ModelScope 流式提取】message.content: '{}'", content);
                            } else {
                                log.warn("【ModelScope 流式警告】未找到 content 字段，delta={}, message={}", 
                                    choice.getDelta(), choice.getMessage());
                            }
                                            
                            Generation generation = new Generation(new AssistantMessage(content));
                            ChatResponse response = new ChatResponse(Collections.singletonList(generation));
                            log.debug("【ModelScope 流式返回】返回 ChatResponse, content='{}'", content);
                            return response;
                        } else {
                            log.warn("【ModelScope 流式警告】choices 为空或不存在");
                        }
                    } catch (Exception e) {
                        log.error("【ModelScope 流式解析失败】无法解析块：{}, 异常：{}", payload, e.getMessage(), e);
                    }
                                    
                    return null;
                })
                .filter(response -> response != null)
                .doOnError(error -> log.error("【ModelScope流式调用异常】{}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("【ModelScope流式调用失败】返回空响应");
                    return Flux.just(emptyResponse());
                });
    }

    private ChatResponse emptyResponse() {
        Generation generation = new Generation(new AssistantMessage(""));
        return new ChatResponse(Collections.singletonList(generation));
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** 将 Spring AI 的 Message 映射为 OpenAI 兼容的 DTO 消息 */
    private List<ModelScopeChatDto.Message> toDtoMessages(List<Message> messages) {
        List<ModelScopeChatDto.Message> list = new ArrayList<>();
        for (Message m : messages) {
            String role;
            if (m instanceof UserMessage) {
                role = "user";
            } else if (m instanceof AssistantMessage) {
                role = "assistant";
            } else {
                // system 等其他角色
                role = safe(m.getMessageType().getValue());
            }
            list.add(new ModelScopeChatDto.Message(role, safe(m.getText())));
        }
        return list;
    }
}
