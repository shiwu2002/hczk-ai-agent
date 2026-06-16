package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import com.hczk.hczkaiagentserver.service.ToolExecuteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具控制器
 * 提供工具执行和工具定义查询端点
 */
@Slf4j
@RestController
@RequestMapping("/tools")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ToolController {

    private final ToolExecuteService toolExecuteService;
    private final ToolDefinitionService toolDefinitionService;
    private final SkillService skillService;

    /**
     * 统一工具执行端点
     * 智能体通过此端点调用平台注册的工具
     *
     * 请求体：
     * {
     *   "tool_name": "knowledge_search",
     *   "arguments": { "query": "...", "collection_name": "products", "agent_id": "M001" },
     *   "session_id": "uuid-optional"
     * }
     */
    @PostMapping("/execute")
    public Result<Map<String, Object>> executeTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("tool_name");
        if (toolName == null || toolName.isBlank()) {
            return Result.error("缺少 tool_name 参数");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.get("arguments");
        if (arguments == null) {
            arguments = Map.of();
        }

        log.info("工具执行请求: tool={}, args={}", toolName, arguments);

        Map<String, Object> result = toolExecuteService.execute(toolName, arguments);
        return Result.success(result);
    }

    /**
     * 获取所有启用的工具定义（供智能体发现工具）
     */
    @GetMapping("/definitions")
    public Result<List<Map<String, Object>>> getToolDefinitions(
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        return Result.success(toolDefinitionService.getActiveToolDefinitions(baseUrl));
    }

    /**
     * 获取工具组目录（init directory）
     * 智能体调用时首先获取此目录，了解有哪些工具组可用
     * 返回格式：[{"name":"knowledge","display_name":"知识库","description":"...","tool_count":5}, ...]
     */
    @GetMapping("/groups")
    public Result<List<Map<String, Object>>> getSkillGroups() {
        List<Skill> skills = skillService.getAllSkillsWithToolCount();
        List<Map<String, Object>> groups = skills.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("name", s.getName());
                    g.put("display_name", s.getDisplayName());
                    g.put("description", s.getDescription());
                    g.put("category", s.getCategory());
                    g.put("tool_count", s.getToolCount());
                    return g;
                })
                .collect(Collectors.toList());
        return Result.success(groups);
    }

    /**
     * 获取指定工具组的工具详情
     * 智能体选择一个工具组后，调用此端点获取该组内所有工具的完整定义
     * 返回 LLM function_call tools 格式，含 name、description、parameters、endpoint
     */
    @GetMapping("/group/{skillName}")
    public Result<List<Map<String, Object>>> getGroupTools(
            @PathVariable String skillName,
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        List<Map<String, Object>> tools = toolDefinitionService.getActiveToolDefinitionsForGroup(skillName, baseUrl);
        return Result.success(tools);
    }
}
