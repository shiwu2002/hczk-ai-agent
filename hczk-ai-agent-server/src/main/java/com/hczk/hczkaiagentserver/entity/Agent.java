package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("agents")
@Data
public class Agent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 智能体类型标识（由注册方自定义，如 customer_service、sales 等）
     */
    @TableField("agent_type")
    private String agentType;

    /**
     * 健康检测接口地址（必填）
     * 注册的智能体必须实现此接口，平台通过 GET 请求检测在线状态
     * 返回格式：{ "status": "ok"|"degraded"|其他, "version": "...", "uptime": ..., "components": {...} }
     */
    @TableField("health_endpoint")
    private String healthEndpoint;

    /**
     * 对话接口地址（必填）
     * 注册的智能体必须实现此接口，平台通过 POST 请求转发对话
     * 请求格式：{ "message": "...", "session_id": "...", "merchant_id": "..." }
     */
    @TableField("chat_endpoint")
    private String chatEndpoint;

    /**
     * 文档上传接口地址（可选）
     * 注册的智能体可实现此接口，平台通过 POST multipart/form-data 上传知识库文档
     * 请求格式：multipart/form-data，字段 file(文件) + collection_name(集合名)
     */
    @TableField("document_endpoint")
    private String documentEndpoint;

    /**
     * 智能体元信息接口地址（可选）
     * GET 请求，返回智能体的能力、工具、模型等信息
     * 返回格式：{ "name": "...", "capabilities": {...}, "tools": [...], "model": "..." }
     */
    @TableField("info_endpoint")
    private String infoEndpoint;

    /**
     * 流式对话接口地址（可选）
     * POST 请求，使用 SSE 格式返回流式对话内容
     * 请求格式与 chatEndpoint 一致，响应格式：text/event-stream
     */
    @TableField("stream_endpoint")
    private String streamEndpoint;

    /**
     * 会话历史接口地址（可选）
     * GET 请求，返回指定会话的历史消息
     * 路径格式：{history_endpoint}/{session_id}
     */
    @TableField("history_endpoint")
    private String historyEndpoint;

    /**
     * 智能体服务版本号（注册时填写，运行时可通过健康检测更新）
     */
    @TableField("version")
    private String version;

    /**
     * 归属用户 ID（雪花ID，管理员创建）
     */
    @TableField("user_id")
    private String userId;

    private AgentStatus status = AgentStatus.ACTIVE;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
