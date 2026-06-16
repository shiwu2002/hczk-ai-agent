package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    /** 智能体 ID（通过智能体调用时使用，优先级高于 modelId 和 model） */
    private Long agentId;

    /** 模型 ID（直接指定模型调用时使用，优先级高于 model） */
    private Long modelId;

    /**
     * 模型标识字符串（OpenAI SDK 兼容格式）
     * 支持格式：
     *   "agent-{id}"  → 解析为 agentId
     *   "model-{id}"  → 解析为 modelId
     *   纯数字        → 解析为 modelId
     *   模型名称       → 按名称查找模型
     */
    private String model;

    /** 简单文本消息（内部调用格式） */
    private String message;

    /** 是否流式响应 */
    private Boolean stream = true;

    /** 是否启用思考模式（请求级覆盖模型配置） */
    private Boolean enableThinking;

    /** OpenAI 兼容格式的消息列表（外部 API 调用格式） */
    private List<OpenAiMessage> messages;

    /**
     * 获取解析后的智能体 ID
     * 优先使用 agentId 字段，其次从 model 字段解析 "agent-{id}" 格式
     */
    public Long getResolvedAgentId() {
        if (agentId != null) return agentId;
        if (model != null && model.startsWith("agent-")) {
            try {
                return Long.parseLong(model.substring(6));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 获取解析后的模型 ID
     * 优先使用 modelId 字段，其次从 model 字段解析 "model-{id}" 或纯数字格式
     */
    public Long getResolvedModelId() {
        if (modelId != null) return modelId;
        if (model != null) {
            if (model.startsWith("model-")) {
                try {
                    return Long.parseLong(model.substring(6));
                } catch (NumberFormatException ignored) {}
            }
            // 纯数字格式，视为模型 ID
            try {
                return Long.parseLong(model);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 获取模型名称（用于按名称查找模型）
     * 当 model 不是 agent-{id}、model-{id}、纯数字格式时，视为模型名称
     */
    public String getModelName() {
        if (model == null) return null;
        if (model.startsWith("agent-") || model.startsWith("model-")) return null;
        try {
            Long.parseLong(model);
            return null; // 纯数字不是名称
        } catch (NumberFormatException e) {
            return model; // 非数字，视为模型名称
        }
    }

    /**
     * 获取实际要发送的消息内容
     * 优先使用 messages 列表中最后一条 user 消息，其次使用 message 字段
     */
    public String getEffectiveMessage() {
        if (messages != null && !messages.isEmpty()) {
            // 取最后一条 user 角色的消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                OpenAiMessage msg = messages.get(i);
                if ("user".equals(msg.getRole())) {
                    return msg.getContent();
                }
            }
            // 没有 user 消息则取最后一条
            return messages.get(messages.size() - 1).getContent();
        }
        return message;
    }

    /**
     * OpenAI 兼容格式的消息对象
     */
    @Data
    public static class OpenAiMessage {
        private String role;
        private String content;
    }
}
