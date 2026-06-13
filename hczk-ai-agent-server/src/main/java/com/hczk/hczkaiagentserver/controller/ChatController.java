package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.dto.ChatRequest;
import com.hczk.hczkaiagentserver.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天接口控制器
 * 支持两种调用方式：
 * 1. 内部调用：/chat/completions（JWT 认证，使用 modelId 或 agentId）
 * 2. OpenAI 兼容调用：/v1/chat/completions（API Key 认证，使用 agentId）
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 内部聊天接口（管理后台/前端使用）
     * 支持 modelId 和 agentId 两种调用方式
     */
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completions(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }

    /**
     * OpenAI 兼容格式接口（外部 API 调用）
     * 路径兼容 /v1/chat/completions，使用 API Key 认证
     * 请求体格式：
     * {
     *   "agentId": 1,
     *   "messages": [{"role": "user", "content": "你好"}],
     *   "stream": true
     * }
     */
    @PostMapping(value = "/v1/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter v1Completions(@RequestBody ChatRequest request) {
        return chatService.streamChat(request);
    }
}
