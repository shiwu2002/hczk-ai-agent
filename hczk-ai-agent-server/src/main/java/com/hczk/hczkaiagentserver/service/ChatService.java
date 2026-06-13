package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AgentMapper;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.util.TokenCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiModelMapper aiModelMapper;
    private final AgentMapper agentMapper;
    private final UserMapper userMapper;
    private final ChatModelFactory chatModelFactory;
    private final BillingService billingService;

    /**
     * 流式聊天
     * 支持两种调用方式：
     * 1. 通过 agentId 调用：使用智能体的系统提示词 + 绑定模型
     * 2. 通过 modelId 调用：直接使用指定模型（无系统提示词）
     *
     * 流式完成后自动统计 token 用量并扣费
     */
    public SseEmitter streamChat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        AiModel model;
        Agent agent = null;
        String systemPrompt = null;
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        if (request.getAgentId() != null) {
            // 通过智能体调用：加载智能体配置和绑定模型
            agent = agentMapper.selectById(request.getAgentId());
            if (agent == null) {
                throw new RuntimeException("智能体不存在");
            }
            if (agent.getStatus() != AgentStatus.ACTIVE) {
                throw new RuntimeException("智能体已停用");
            }

            model = aiModelMapper.selectById(agent.getModelId());
            if (model == null) {
                throw new RuntimeException("智能体关联的模型不存在");
            }

            // 添加智能体的系统提示词（description 字段）
            if (agent.getDescription() != null && !agent.getDescription().trim().isEmpty()) {
                systemPrompt = agent.getDescription();
                messages.add(new SystemMessage(systemPrompt));
            }

            log.info("通过智能体调用: agentId={}, agentName={}, modelId={}, modelName={}",
                    agent.getId(), agent.getName(), model.getId(), model.getName());
        } else if (request.getModelId() != null) {
            // 直接指定模型调用
            model = aiModelMapper.selectById(request.getModelId());
            if (model == null) {
                throw new RuntimeException("模型不存在");
            }
        } else {
            throw new RuntimeException("请指定 agentId 或 modelId");
        }

        if (model.getStatus() != ModelStatus.ACTIVE) {
            throw new RuntimeException("模型已停用");
        }

        // 估算输入 token 数
        long inputTokens = TokenCounter.estimateInputTokens(systemPrompt, effectiveMessage);

        // 添加用户消息
        messages.add(new UserMessage(effectiveMessage));

        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        Prompt prompt = new Prompt(messages);

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        // 累积输出内容，用于估算输出 token
        StringBuilder outputContent = new StringBuilder();

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        Flux<ChatResponse> flux = chatModel.stream(prompt);

        // 保存引用，用于计费
        final AiModel finalModel = model;
        final Agent finalAgent = agent;

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
                    }
                },
                error -> {
                    log.error("流式调用失败: {}", error.getMessage(), error);
                    if (completed.compareAndSet(false, true)) {
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
                        processBilling(finalModel, finalAgent, inputTokens, outputContent.toString());
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
     * 4. 更新智能体统计
     */
    private void processBilling(AiModel model, Agent agent, long inputTokens, String outputText) {
        try {
            long outputTokens = TokenCounter.estimateTokens(outputText);
            long totalTokens = inputTokens + outputTokens;

            // 根据模型定价计算费用（价格单位：元/千Tokens）
            BigDecimal inputCost = model.getInputPrice() != null
                    ? model.getInputPrice().multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal outputCost = model.getOutputPrice() != null
                    ? model.getOutputPrice().multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal totalCost = inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);

            // 获取当前用户 ID
            Long userId = getCurrentUserId();
            if (userId == null) {
                log.warn("无法获取当前用户 ID，跳过计费");
                return;
            }

            // 扣减用户余额
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                String detail = String.format("模型[%s]调用 - 输入:%d tokens, 输出:%d tokens",
                        model.getName(), inputTokens, outputTokens);
                boolean success = billingService.deductBalance(userId, totalCost, inputTokens, outputTokens, detail);
                if (success) {
                    log.info("计费成功: userId={}, cost={}元, inputTokens={}, outputTokens={}",
                            userId, totalCost, inputTokens, outputTokens);
                } else {
                    log.warn("计费失败（余额不足）: userId={}, cost={}元", userId, totalCost);
                }
            }

            // 更新智能体统计
            if (agent != null) {
                agent.setTotalCalls(agent.getTotalCalls() + 1);
                agent.setTotalTokens(agent.getTotalTokens() + totalTokens);
                agentMapper.updateById(agent);
            }

        } catch (Exception e) {
            log.error("计费处理异常: {}", e.getMessage(), e);
            // 计费异常不影响已完成的对话
        }
    }

    /**
     * 获取当前用户 ID
     * 1. API Key 认证：details 中直接存储了 userId
     * 2. JWT 认证：principal 是 username，通过 UserMapper 查找 userId
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }

        // API Key 认证：details 中直接存储了 userId
        if (auth.getDetails() instanceof Long) {
            return (Long) auth.getDetails();
        }

        // JWT 认证：principal 是 username，通过数据库查找 userId
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
}
