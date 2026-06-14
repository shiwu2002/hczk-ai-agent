package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.ChatLog;
import com.hczk.hczkaiagentserver.mapper.ChatLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatLogController {

    private final ChatLogMapper chatLogMapper;

    /**
     * 查询所有对话记录（管理员）
     */
    @GetMapping
    public Result<List<ChatLog>> getAllChatLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ChatLog> wrapper = new LambdaQueryWrapper<ChatLog>()
                .orderByDesc(ChatLog::getCreatedAt);
        if (userId != null) wrapper.eq(ChatLog::getUserId, userId);
        if (apiKeyId != null) wrapper.eq(ChatLog::getApiKeyId, apiKeyId);
        if (modelName != null) wrapper.eq(ChatLog::getModelName, modelName);
        if (status != null) wrapper.eq(ChatLog::getStatus, status);
        return Result.success(chatLogMapper.selectList(wrapper));
    }

    /**
     * 查询单条对话记录详情
     */
    @GetMapping("/{id}")
    public Result<ChatLog> getChatLog(@PathVariable Long id) {
        return Result.success(chatLogMapper.selectById(id));
    }

    /**
     * 查询指定用户的对话记录
     */
    @GetMapping("/user/{userId}")
    public Result<List<ChatLog>> getUserChatLogs(@PathVariable Long userId) {
        return Result.success(chatLogMapper.selectList(
                new LambdaQueryWrapper<ChatLog>()
                        .eq(ChatLog::getUserId, userId)
                        .orderByDesc(ChatLog::getCreatedAt)));
    }
}
