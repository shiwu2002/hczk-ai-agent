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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiModelMapper aiModelMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;
    private final ChatLogMapper chatLogMapper;
    private final ChatModelFactory chatModelFactory;
    private final BillingService billingService;

    /**
     * 流式聊天
     * 计费流程：API Key → 查询用户 → 余额检查 → 扣减余额 → 记录账单 → 更新API Key统计
     * 必须通过API Key认证调用，模型由API Key绑定的modelIds决定
     */
    public SseEmitter streamChat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        // 提前获取认证信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = resolveUserId(auth);
        Long apiKeyId = resolveApiKeyId(auth);

        if (apiKeyId == null) {
            throw new RuntimeException("必须通过API Key认证才能调用");
        }

        // 1. API Key → 查询用户
        ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
        if (apiKey == null || !"active".equals(apiKey.getStatus())) {
            throw new RuntimeException("API Key无效或已禁用");
        }

        if (userId == null) {
            throw new RuntimeException("无法获取用户信息");
        }

        // 2. 确定调用的模型（必须在API Key绑定的模型范围内）
        AiModel model = resolveModel(request, apiKey);

        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        // 3. 余额预检查
        if (apiKey.getUnitPrice() != null && apiKey.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal balance = billingService.getUserBalanceFromCache(userId);
            if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("余额不足，请先充值");
            }
        }

        // 估算输入 token 数
        long inputTokens = TokenCounter.estimateInputTokens(null, effectiveMessage);

        // 添加用户消息
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(effectiveMessage));

        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        Prompt prompt = new Prompt(messages);

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        StringBuilder outputContent = new StringBuilder();
        long startTime = System.currentTimeMillis();

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        Flux<ChatResponse> flux = chatModel.stream(prompt);

        final AiModel finalModel = model;
        final Long finalApiKeyId = apiKeyId;
        final Long finalUserId = userId;
        final BigDecimal unitPrice = apiKey.getUnitPrice() != null ? apiKey.getUnitPrice() : BigDecimal.ZERO;

        flux.subscribe(
                chatResponse -> {
                    if (completed.get()) return;
                    try {
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
                            outputContent.append(content);
                            emitter.send(SseEmitter.event().data(content));
                        }
                    } catch (IOException e) {
                        completed.set(true);
                        log.debug("客户端断开连接，停止发送SSE数据");
                    }
                },
                error -> {
                    log.error("流式调用失败: {}", error.getMessage(), error);
                    if (completed.compareAndSet(false, true)) {
                        saveChatLog(finalModel, effectiveMessage, outputContent.toString(),
                                inputTokens, finalUserId, finalApiKeyId, startTime, "failed", error.getMessage(), BigDecimal.ZERO);
                        emitter.completeWithError(error);
                    }
                },
                () -> {
                    if (completed.compareAndSet(false, true)) {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                            emitter.complete();
                        } catch (IOException ignored) {
                        }
                        // 流式完成后统计 token 并扣费
                        processBilling(finalModel, effectiveMessage, inputTokens, outputContent.toString(),
                                finalUserId, finalApiKeyId, unitPrice, startTime);
                    }
                }
        );

        return emitter;
    }

    /**
     * 根据请求和API Key绑定的模型确定调用的模型
     * 请求指定的模型必须在API Key的modelIds范围内
     */
    private AiModel resolveModel(ChatRequest request, ApiKey apiKey) {
        List<Long> boundModelIds = apiKey.getModelIds();

        Long resolvedModelId = request.getResolvedModelId();
        String modelName = request.getModelName();

        if (resolvedModelId != null) {
            // 指定了模型ID，校验是否在API Key绑定范围内
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

        if (modelName != null) {
            // 按名称查找
            AiModel model = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModel>()
                            .eq(AiModel::getModelId, modelName)
                            .eq(AiModel::getStatus, ModelStatus.ACTIVE));
            if (model == null) {
                throw new RuntimeException("模型不存在: " + modelName);
            }
            if (boundModelIds != null && !boundModelIds.isEmpty() && !boundModelIds.contains(model.getId())) {
                throw new RuntimeException("模型 " + modelName + " 不在API Key绑定范围内");
            }
            log.info("按名称查找模型调用: modelName={}, modelId={}", modelName, model.getId());
            return model;
        }

        // 未指定模型，使用API Key绑定的第一个模型
        if (boundModelIds == null || boundModelIds.isEmpty()) {
            throw new RuntimeException("API Key未绑定任何模型，请先配置");
        }
        AiModel model = aiModelMapper.selectById(boundModelIds.get(0));
        if (model == null) {
            throw new RuntimeException("API Key绑定的模型不存在");
        }
        log.info("使用API Key默认模型: modelId={}, modelName={}", model.getId(), model.getName());
        return model;
    }

    /**
     * 流式完成后处理计费
     * 费用 = API Key的unitPrice × (inputTokens + outputTokens) / 1000
     */
    private void processBilling(AiModel model, String inputContent, long inputTokens, String outputText,
                                Long userId, Long apiKeyId, BigDecimal unitPrice, long startTime) {
        try {
            long outputTokens = TokenCounter.estimateTokens(outputText);

            // 根据API Key的统一Token单价计算费用
            long totalTokens = inputTokens + outputTokens;
            BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(totalTokens))
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

            log.info("计费计算: model={}, inputTokens={}, outputTokens={}, unitPrice={}, totalCost={}, userId={}, apiKeyId={}",
                    model.getName(), inputTokens, outputTokens, unitPrice, totalCost, userId, apiKeyId);

            String detail = String.format("API Key调用模型[%s] - 输入:%d tokens, 输出:%d tokens, 单价:%s元/千Tokens",
                    model.getName(), inputTokens, outputTokens, unitPrice.toPlainString());

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
     */
    private void saveChatLog(AiModel model, String inputContent, String outputContent,
                             long inputTokens, Long userId, Long apiKeyId, long startTime,
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
     * 从认证信息中解析用户 ID
     */
    private Long resolveUserId(Authentication auth) {
        if (auth == null) return null;

        // API Key 认证：details 是 Map
        if (auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object userId = details.get("userId");
            if (userId instanceof Long) return (Long) userId;
        }

        // JWT 认证：principal 是 username
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            String username = (String) principal;
            User user = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username));
            return user != null ? user.getId() : null;
        }

        return null;
    }

    /**
     * 从认证信息中解析 API Key ID
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
}
