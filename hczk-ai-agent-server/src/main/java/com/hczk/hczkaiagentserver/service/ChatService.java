package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.ChatLog;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
import com.hczk.hczkaiagentserver.mapper.ApiKeyMapper;
import com.hczk.hczkaiagentserver.mapper.ChatLogMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.util.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天服务
 *
 * 核心职责：处理流式聊天请求，管理计费流程
 *
 * 计费流程：
 * 1. API Key认证 → 查询API Key信息（unitPrice、modelIds）
 * 2. 验证模型在API Key绑定范围内
 * 3. 余额预检查（Redis缓存）
 * 4. 调用AI模型流式输出
 * 5. 流式完成后估算Token → 按API Key的unitPrice计算费用
 * 6. 扣减余额 → 记录账单 → 更新API Key统计
 *
 * 计费公式：费用 = (inputTokens + outputTokens) / 1000 × unitPrice
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** AI模型数据访问 */
    private final AiModelMapper aiModelMapper;

    /** API Key数据访问 */
    private final ApiKeyMapper apiKeyMapper;

    /** 用户数据访问 */
    private final UserMapper userMapper;

    /** 对话日志数据访问 */
    private final ChatLogMapper chatLogMapper;

    /** AI模型工厂（根据模型配置创建ChatModel实例） */
    private final ChatModelFactory chatModelFactory;

    /** 计费服务（扣减余额、记录账单） */
    private final BillingService billingService;

    /**
     * 流式聊天入口
     *
     * 处理流程：
     * 1. 校验消息非空
     * 2. 从SecurityContext提取认证信息（userId、apiKeyId）
     * 3. 查询API Key并验证状态（status=0可用）
     * 4. 确定调用模型（必须在API Key绑定的modelIds范围内）
     * 5. 余额预检查
     * 6. 估算输入Token，创建SSE流式响应
     * 7. 流式完成后触发计费
     *
     * @param request 聊天请求（包含消息内容、可选模型ID/名称）
     * @return SseEmitter 流式响应发射器
     * @throws RuntimeException 消息为空、未认证、API Key无效、模型不可用、余额不足
     */
    public SseEmitter streamChat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        // 从SecurityContext提取认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = resolveUserId(auth);
        Long apiKeyId = resolveApiKeyId(auth);

        // 必须通过API Key认证
        if (apiKeyId == null) {
            throw new RuntimeException("必须通过API Key认证才能调用");
        }

        // 1. API Key → 查询用户，验证API Key状态（0=可用，1=禁用）
        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null || apiKey.getStatus() != 0) {
            throw new RuntimeException("API Key无效或已禁用");
        }

        if (userId == null) {
            throw new RuntimeException("无法获取用户信息");
        }

        // 2. 确定调用的模型（必须在API Key绑定的模型范围内）
        AiModel model = resolveModel(request, apiKey);

        // 验证模型启用状态
        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        // 3. 余额预检查（仅当unitPrice > 0时检查，免费模型跳过）
        if (apiKey.getUnitPrice() != null && apiKey.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = billingService.getUserBalanceFromCache(userId);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("余额不足，请先充值");
            }
        }

        // 估算输入Token数（2字符=1Token）
        long inputTokens = TokenCounter.estimateInputTokens(null, effectiveMessage);

        // 构建消息列表
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(effectiveMessage));

        // 通过模型工厂创建ChatModel实例，设置请求级 thinking 参数
        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        try {
            chatModelFactory.setRequestThinking(request.getEnableThinking());
            Prompt prompt = new Prompt(messages);

            // 创建SSE发射器（0L=不超时）
            SseEmitter emitter = new SseEmitter(0L);
            AtomicBoolean completed = new AtomicBoolean(false);
            StringBuilder outputContent = new StringBuilder();
            StringBuilder reasoningContent = new StringBuilder();
            AtomicBoolean inThinkingPhase = new AtomicBoolean(false);
            long startTime = System.currentTimeMillis();

            // SSE生命周期回调
            emitter.onCompletion(() -> {
                completed.set(true);
                chatModelFactory.clearRequestThinking();
            });
            emitter.onTimeout(() -> {
                completed.set(true);
                chatModelFactory.clearRequestThinking();
            });
            emitter.onError(e -> {
                completed.set(true);
                chatModelFactory.clearRequestThinking();
            });

            // 订阅流式响应
            Flux<ChatResponse> flux = chatModel.stream(prompt);

            // Lambda中使用的final变量
            final AiModel finalModel = model;
            final Long finalApiKeyId = apiKeyId;
            final String finalUserId = userId;
            final BigDecimal unitPrice = apiKey.getUnitPrice() != null ? apiKey.getUnitPrice() : BigDecimal.ZERO;
            final String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            final long createdSeconds = System.currentTimeMillis() / 1000;
            final String modelName = model.getModelId() != null ? model.getModelId() : model.getName();

            flux.subscribe(
                    // 数据回调：逐块发送SSE事件（OpenAI 兼容格式）
                    chatResponse -> {
                        if (completed.get()) return;
                        try {
                            var result = chatResponse.getResult();
                            var output = result.getOutput();
                            String content = output.getText();
                            // 通过metadata中的reasoning标记区分思考内容和回复内容
                            boolean isReasoning = Boolean.TRUE.equals(output.getMetadata().get("reasoning"));

                            if (content != null && !content.isEmpty()) {
                                if (isReasoning) {
                                    // 思考内容
                                    reasoningContent.append(content);
                                    // 发送思考内容的SSE事件（DashScope兼容格式：reasoning_content字段）
                                    String chunk = String.format(
                                        "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"%s\"},\"finish_reason\":null}]}",
                                        chatId, createdSeconds, modelName, escapeJson(content)
                                    );
                                    emitter.send(SseEmitter.event().data(chunk));
                                } else {
                                    // 正式回复内容
                                    if (inThinkingPhase.get()) {
                                        inThinkingPhase.set(false);
                                        log.info("思考阶段结束，思考内容: {}字符", reasoningContent.length());
                                    }
                                    outputContent.append(content);
                                    // OpenAI 标准流式格式
                                    String chunk = String.format(
                                        "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                                        chatId, createdSeconds, modelName, escapeJson(content)
                                    );
                                    emitter.send(SseEmitter.event().data(chunk));
                                }
                                log.debug("SSE发送chunk: {}字符, reasoning={}", content.length(), isReasoning);
                            }
                        } catch (IOException e) {
                            completed.set(true);
                            log.debug("客户端断开连接，停止发送SSE数据");
                        }
                    },
                    // 错误回调：记录失败日志，发送错误事件
                    error -> {
                        log.error("流式调用失败: {}", error.getMessage(), error);
                        if (completed.compareAndSet(false, true)) {
                            try {
                                // 发送错误信息给客户端（OpenAI兼容格式）
                                String errorChunk = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"[错误] %s\"},\"finish_reason\":\"stop\"}]}",
                                    chatId, createdSeconds, modelName, escapeJson(error.getMessage() != null ? error.getMessage() : "未知错误")
                                );
                                emitter.send(SseEmitter.event().data(errorChunk));
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException ignored) {
                            }
                            saveChatLog(finalModel, effectiveMessage, outputContent.toString(),
                                    inputTokens, finalUserId, finalApiKeyId, startTime, "failed", error.getMessage(), BigDecimal.ZERO);
                            chatModelFactory.clearRequestThinking();
                        }
                    },
                    // 完成回调：发送[DONE]标记，触发计费
                    () -> {
                        log.info("流式响应完成，总输出: {}字符, 思考内容: {}字符", outputContent.length(), reasoningContent.length());
                        if (completed.compareAndSet(false, true)) {
                            try {
                                // 发送最后一个 chunk（finish_reason: stop）
                                String finalChunk = String.format(
                                    "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                                    chatId, createdSeconds, modelName
                                );
                                emitter.send(SseEmitter.event().data(finalChunk));
                                emitter.send(SseEmitter.event().data("[DONE]"));
                                emitter.complete();
                            } catch (IOException ignored) {
                            }
                            // 流式完成后统计Token并扣费
                            processBilling(finalModel, effectiveMessage, inputTokens, outputContent.toString(),
                                    finalUserId, finalApiKeyId, unitPrice, startTime);
                            chatModelFactory.clearRequestThinking();
                        }
                    }
            );

            return emitter;
        } catch (Exception e) {
            chatModelFactory.clearRequestThinking();
            throw e;
        }
    }

    /**
     * 非流式聊天入口
     *
     * 处理流程与 streamChat 相同，但返回完整的 JSON 响应而非 SSE 流
     *
     * @param request 聊天请求（包含消息内容、可选模型ID/名称）
     * @return OpenAI 兼容格式的完整响应 Map
     * @throws RuntimeException 消息为空、未认证、API Key无效、模型不可用、余额不足
     */
    public Map<String, Object> chat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        // 从SecurityContext提取认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = resolveUserId(auth);
        Long apiKeyId = resolveApiKeyId(auth);

        if (apiKeyId == null) {
            throw new RuntimeException("必须通过API Key认证才能调用");
        }

        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null || apiKey.getStatus() != 0) {
            throw new RuntimeException("API Key无效或已禁用");
        }

        if (userId == null) {
            throw new RuntimeException("无法获取用户信息");
        }

        AiModel model = resolveModel(request, apiKey);

        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        if (apiKey.getUnitPrice() != null && apiKey.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = billingService.getUserBalanceFromCache(userId);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("余额不足，请先充值");
            }
        }

        long inputTokens = TokenCounter.estimateInputTokens(null, effectiveMessage);

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(effectiveMessage));

        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        try {
            chatModelFactory.setRequestThinking(request.getEnableThinking());
            Prompt prompt = new Prompt(messages);
            long startTime = System.currentTimeMillis();

            // 同步调用
            ChatResponse chatResponse = chatModel.call(prompt);

            String outputText = "";
            if (chatResponse != null && chatResponse.getResult() != null
                    && chatResponse.getResult().getOutput() != null) {
                outputText = chatResponse.getResult().getOutput().getText();
                if (outputText == null) outputText = "";
            }

            // 计费
            BigDecimal unitPrice = apiKey.getUnitPrice() != null ? apiKey.getUnitPrice() : BigDecimal.ZERO;
            processBilling(model, effectiveMessage, inputTokens, outputText, userId, apiKeyId, unitPrice, startTime);

            // 构建 OpenAI 兼容响应
            String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            long createdSeconds = System.currentTimeMillis() / 1000;
            String modelName = model.getModelId() != null ? model.getModelId() : model.getName();
            long outputTokens = TokenCounter.estimateTokens(outputText);

            return Map.of(
                "id", chatId,
                "object", "chat.completion",
                "created", createdSeconds,
                "model", modelName,
                "choices", List.of(Map.of(
                    "index", 0,
                    "message", Map.of(
                        "role", "assistant",
                        "content", outputText
                    ),
                    "finish_reason", "stop"
                )),
                "usage", Map.of(
                    "prompt_tokens", inputTokens,
                    "completion_tokens", outputTokens,
                    "total_tokens", inputTokens + outputTokens
                )
            );
        } finally {
            chatModelFactory.clearRequestThinking();
        }
    }

    /**
     * 根据请求和API Key绑定的模型确定调用的模型
     *
     * 优先级：
     * 1. 请求指定了模型ID → 校验是否在绑定范围内（model_ids为空时允许所有）
     * 2. 请求指定了模型名称 → 按名称查找并校验绑定范围（model_ids为空时允许所有）
     * 3. 未指定模型 → 使用API Key绑定的第一个模型，model_ids为空时使用第一个可用模型
     *
     * @param request 聊天请求（可能包含modelId或modelName）
     * @param apiKey  API Key实体（包含绑定的modelIds列表）
     * @return 确定的AI模型
     * @throws RuntimeException 模型不在绑定范围、模型不存在、无可用模型
     */
    private AiModel resolveModel(ChatRequest request, ApiKey apiKey) {
        List<Long> boundModelIds = apiKey.getModelIds();

        Long resolvedModelId = request.getResolvedModelId();
        String modelName = request.getModelName();

        // 优先级1：指定了模型ID
        if (resolvedModelId != null) {
            // 校验是否在API Key绑定范围内（model_ids为空时允许访问所有模型）
            if (boundModelIds != null && !boundModelIds.isEmpty() && !boundModelIds.contains(resolvedModelId)) {
                throw new RuntimeException("模型ID " + resolvedModelId + " 不在API Key绑定范围内");
            }
            AiModel model = aiModelMapper.selectById(resolvedModelId);
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }
            log.info("指定模型调用: modelId={}, modelName={}", model.getId(), model.getName());
            return model;
        }

        // 优先级2：指定了模型名称
        if (modelName != null) {
            AiModel model = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModel>()
                            .eq(AiModel::getModelId, modelName)
                            .eq(AiModel::getStatus, ModelStatus.ACTIVE));
            if (model == null) {
                throw new RuntimeException("模型不存在: " + modelName);
            }
            // 校验是否在API Key绑定范围内（model_ids为空时允许访问所有模型）
            if (boundModelIds != null && !boundModelIds.isEmpty() && !boundModelIds.contains(model.getId())) {
                throw new RuntimeException("模型 " + modelName + " 不在API Key绑定范围内");
            }
            log.info("按名称查找模型调用: modelName={}, modelId={}", modelName, model.getId());
            return model;
        }

        // 优先级3：未指定模型，使用API Key绑定的第一个模型，或第一个可用模型
        if (boundModelIds != null && !boundModelIds.isEmpty()) {
            AiModel model = aiModelMapper.selectById(boundModelIds.get(0));
            if (model == null) {
                throw new RuntimeException("API Key绑定的模型不存在");
            }
            log.info("使用API Key默认模型: modelId={}, modelName={}", model.getId(), model.getName());
            return model;
        }
        // model_ids为空时，使用第一个可用模型
        AiModel defaultModel = aiModelMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getStatus, ModelStatus.ACTIVE)
                        .last("LIMIT 1"));
        if (defaultModel == null) {
            throw new RuntimeException("没有可用的模型");
        }
        log.info("API Key未绑定模型，使用默认可用模型: modelId={}, modelName={}", defaultModel.getId(), defaultModel.getName());
        return defaultModel;
    }

    /**
     * 流式完成后处理计费
     *
     * 计费公式：费用 = (inputTokens + outputTokens) / 1000 × unitPrice
     * - unitPrice 来自API Key的统一Token单价（元/千Tokens）
     * - 保留6位小数，四舍五入
     *
     * 处理步骤：
     * 1. 估算输出Token数
     * 2. 计算总费用
     * 3. 调用billingService扣减余额并记录账单
     * 4. 保存对话日志
     *
     * @param model       调用的AI模型
     * @param inputContent 输入内容
     * @param inputTokens  输入Token数
     * @param outputText   输出内容
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param unitPrice    API Key的统一Token单价
     * @param startTime    请求开始时间戳
     */
    private void processBilling(AiModel model, String inputContent, long inputTokens, String outputText,
                                String userId, Long apiKeyId, BigDecimal unitPrice, long startTime) {
        try {
            // 估算输出Token数（2字符=1Token，最小1）
            long outputTokens = TokenCounter.estimateTokens(outputText);

            // 根据API Key的统一Token单价计算费用
            long totalTokens = inputTokens + outputTokens;
            BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(totalTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

            log.info("计费计算: model={}, inputTokens={}, outputTokens={}, unitPrice={}, totalCost={}, userId={}, apiKeyId={}",
                    model.getName(), inputTokens, outputTokens, unitPrice, totalCost, userId, apiKeyId);

            // 构建计费明细描述
            String detail = String.format("API Key调用模型[%s] - 输入:%d tokens, 输出:%d tokens, 单价:%s元/千Tokens",
                    model.getName(), inputTokens, outputTokens, unitPrice.toPlainString());

            // 扣减余额 → 记录账单 → 更新API Key统计
            boolean success = billingService.deductBalance(userId, apiKeyId, model.getId(), totalCost, inputTokens, outputTokens, detail);
            if (success) {
                log.info("计费成功: userId={}, apiKeyId={}, cost={}元", userId, apiKeyId, totalCost);
            } else {
                log.warn("计费失败（余额不足）: userId={}, cost={}元", userId, totalCost);
            }

            // 保存对话日志
            saveChatLog(model, inputContent, outputText, inputTokens, userId, apiKeyId, startTime, "success", null, totalCost);

        } catch (Exception e) {
            log.error("计费处理异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存对话记录到 chat_logs 表
     *
     * @param model        AI模型
     * @param inputContent 输入内容
     * @param outputContent 输出内容
     * @param inputTokens  输入Token数
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param startTime    请求开始时间戳
     * @param status       状态：success / failed
     * @param errorMessage 错误信息（成功时为null）
     * @param cost         本次调用费用
     */
    private void saveChatLog(AiModel model, String inputContent, String outputContent,
                             long inputTokens, String userId, Long apiKeyId, long startTime,
                             String status, String errorMessage, BigDecimal cost) {
        try {
            long outputTokens = TokenCounter.estimateTokens(outputContent);
            long durationMs = System.currentTimeMillis() - startTime;

            ChatLog chatLog = new ChatLog();
            chatLog.setUserId(userId);
            chatLog.setApiKeyId(apiKeyId);
            chatLog.setModelId(model.getId());
            chatLog.setModelName(model.getName());
            chatLog.setInputContent(inputContent);
            chatLog.setOutputContent(outputContent);
            chatLog.setInputTokens(inputTokens);
            chatLog.setOutputTokens(outputTokens);
            chatLog.setCost(cost);
            chatLog.setDurationMs(durationMs);
            chatLog.setStatus(status);
            chatLog.setErrorMessage(errorMessage);
            chatLogMapper.insert(chatLog);
        } catch (Exception e) {
            log.warn("保存对话日志失败: {}", e.getMessage());
        }
    }

    /**
     * 从认证信息中解析用户ID
     *
     * 支持两种认证方式：
     * - API Key认证：details是Map，直接取userId
     * - JWT认证：principal是username，需查库获取userId
     *
     * @param auth 认证信息
     * @return 用户ID（雪花ID字符串），无法解析时返回null
     */
    private String resolveUserId(Authentication auth) {
        if (auth == null) return null;

        // API Key认证：details中包含userId和apiKeyId
        if (auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object userId = details.get("userId");
            if (userId instanceof String) return (String) userId;
        }

        // JWT认证：principal是username，查库获取userId
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            String username = (String) principal;
            User user = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username));
            return user != null ? user.getUserId() : null;
        }

        return null;
    }

    /**
     * 从认证信息中解析API Key ID
     *
     * 仅API Key认证方式有apiKeyId（JWT认证无此信息）
     *
     * @param auth 认证信息
     * @return API Key ID，非API Key认证时返回null
     */
    private Long resolveApiKeyId(Authentication auth) {
        if (auth == null) return null;
        if (auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object apiKeyId = details.get("apiKeyId");
            if (apiKeyId instanceof Long) return (Long) apiKeyId;
        }
        return null;
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
