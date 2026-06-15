package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.entity.ChatLog;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
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
    private final UserMapper userMapper;
    private final ChatLogMapper chatLogMapper;
    private final ChatModelFactory chatModelFactory;
    private final BillingService billingService;

    /**
     * 流式聊天
     * 支持通过 modelId 或模型名称指定模型，未指定时使用默认模型
     * 流式完成后自动统计 token 用量并扣费
     */
    public SseEmitter streamChat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        AiModel model;
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        Long resolvedModelId = request.getResolvedModelId();
        String modelName = request.getModelName();

        if (resolvedModelId != null) {
            // 直接指定模型 ID 调用
            model = aiModelMapper.selectById(resolvedModelId);
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }
            log.info("直接指定模型调用: modelId={}, modelName={}", model.getId(), model.getName());
        } else if (modelName != null) {
            // 按模型名称查找（OpenAI SDK 兼容，如 model="qwen-plus"）
            model = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModel>()
                            .eq(AiModel::getModelId, modelName)
                            .eq(AiModel::getStatus, ModelStatus.ACTIVE));
            if (model == null) {
                throw new RuntimeException("模型不存在: " + modelName);
            }
            log.info("按名称查找模型调用: modelName={}, modelId={}", modelName, model.getId());
        } else {
            // 未指定模型，使用第一个可用模型作为默认
            model = aiModelMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiModel>()
                            .eq(AiModel::getStatus, ModelStatus.ACTIVE)
                            .orderByAsc(AiModel::getId)
                            .last("LIMIT 1"));
            if (model == null) {
                throw new RuntimeException("没有可用的模型，请联系管理员");
            }
            log.info("未指定模型，使用默认模型: modelId={}, modelName={}", model.getId(), model.getName());
        }

        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        // 估算输入 token 数
        long inputTokens = TokenCounter.estimateInputTokens(null, effectiveMessage);

        // 添加用户消息
        messages.add(new UserMessage(effectiveMessage));

        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        Prompt prompt = new Prompt(messages);

        // 在请求线程上提前获取认证信息（Flux 回调在其他线程，SecurityContext 不可用）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = resolveUserId(auth);
        Long apiKeyId = resolveApiKeyId(auth);

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        // 累积输出内容，用于估算输出 token
        StringBuilder outputContent = new StringBuilder();
        // 记录开始时间，用于计算响应耗时
        long startTime = System.currentTimeMillis();

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        Flux<ChatResponse> flux = chatModel.stream(prompt);

        // 保存引用，用于计费
        final AiModel finalModel = model;

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
                        // 记录失败的对话日志
                        saveChatLog(finalModel, effectiveMessage, outputContent.toString(),
                                inputTokens, userId, apiKeyId, startTime, "failed", error.getMessage());
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
                        // 流式完成后统计 token 并扣费（使用提前获取的 userId/apiKeyId）
                        processBilling(finalModel, effectiveMessage, inputTokens, outputContent.toString(), userId, apiKeyId, startTime);
                    }
                }
        );

        return emitter;
    }

    /**
     * 流式完成后处理计费
     * 1. 估算输出 token 数
     * 2. 根据模型定价计算费用
     * 3. 扣减用户余额
     */
    private void processBilling(AiModel model, String inputContent, long inputTokens, String outputText, Long userId, Long apiKeyId, long startTime) {
        try {
            long outputTokens = TokenCounter.estimateTokens(outputText);

            // 根据模型定价计算费用（价格单位：元/千Tokens）
            BigDecimal inputCost = model.getInputPrice() != null
                    ? model.getInputPrice().multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal outputCost = model.getOutputPrice() != null
                    ? model.getOutputPrice().multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal totalCost = inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);

            log.info("计费计算: model={}, inputTokens={}, outputTokens={}, inputCost={}, outputCost={}, totalCost={}, userId={}, apiKeyId={}",
                    model.getName(), inputTokens, outputTokens, inputCost, outputCost, totalCost, userId, apiKeyId);

            if (userId == null) {
                log.warn("无法获取当前用户 ID，跳过计费");
                return;
            }

            // 无论费用是否为0，都记录用量明细；费用>0时扣减余额
            String detail = String.format("模型[%s]调用 - 输入:%d tokens, 输出:%d tokens",
                    model.getName(), inputTokens, outputTokens);

            boolean success = billingService.deductBalance(userId, apiKeyId, totalCost, inputTokens, outputTokens, detail);
            if (success) {
                log.info("计费成功: userId={}, apiKeyId={}, cost={}元", userId, apiKeyId, totalCost);
            } else {
                log.warn("计费失败（余额不足）: userId={}, cost={}元", userId, totalCost);
            }

            // 保存对话日志
            saveChatLog(model, inputContent, outputText, inputTokens, userId, apiKeyId, startTime, "success", null);

        } catch (Exception e) {
            log.error("计费处理异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存对话记录到 chat_logs 表
     */
    private void saveChatLog(AiModel model, String inputContent, String outputContent,
                             long inputTokens, Long userId, Long apiKeyId, long startTime,
                             String status, String errorMessage) {
        try {
            long outputTokens = TokenCounter.estimateTokens(outputContent);
            long durationMs = System.currentTimeMillis() - startTime;

            BigDecimal cost = BigDecimal.ZERO;
            if (model.getInputPrice() != null) {
                cost = cost.add(model.getInputPrice().multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
            }
            if (model.getOutputPrice() != null) {
                cost = cost.add(model.getOutputPrice().multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP));
            }

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
