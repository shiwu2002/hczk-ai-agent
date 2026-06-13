package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import com.hczk.hczkaiagentserver.mapper.AgentMapper;
import com.hczk.hczkaiagentserver.mapper.AiModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiModelMapper aiModelMapper;
    private final AgentMapper agentMapper;
    private final ChatModelFactory chatModelFactory;

    /**
     * 流式聊天
     * 支持两种调用方式：
     * 1. 通过 agentId 调用：使用智能体的系统提示词 + 绑定模型
     * 2. 通过 modelId 调用：直接使用指定模型（无系统提示词）
     */
    public SseEmitter streamChat(ChatRequest request) {
        String effectiveMessage = request.getEffectiveMessage();
        if (effectiveMessage == null || effectiveMessage.trim().isEmpty()) {
            throw new RuntimeException("消息内容不能为空");
        }

        AiModel model;
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        if (request.getAgentId() != null) {
            // 通过智能体调用：加载智能体配置和绑定模型
            Agent agent = agentMapper.selectById(request.getAgentId());
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
                messages.add(new SystemMessage(agent.getDescription()));
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

        // 添加用户消息
        messages.add(new UserMessage(effectiveMessage));

        ChatModel chatModel = chatModelFactory.getOrCreate(model);
        Prompt prompt = new Prompt(messages);

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);

        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        Flux<ChatResponse> flux = chatModel.stream(prompt);

        flux.subscribe(
                chatResponse -> {
                    if (completed.get()) return;
                    try {
                        String content = chatResponse.getResult().getOutput().getText();
                        if (content != null && !content.isEmpty()) {
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
                    }
                }
        );

        return emitter;
    }
}
