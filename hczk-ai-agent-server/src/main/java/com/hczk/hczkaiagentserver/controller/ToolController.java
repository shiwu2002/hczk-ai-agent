package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import com.hczk.hczkaiagentserver.service.ToolExecuteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具控制器
 * 提供工具执行和工具定义查询端点
 *
 * v10 新增：基于用户可见性的 skills 暴露接口
 * - 智能体调用 /api/tools/groups 时按用户身份过滤可见的 skills
 * - 智能体调用 /api/tools/group/{skillName} 时校验用户访问权限
 * - 智能体调用 /api/tools/execute 时校验用户对目标工具的访问权限
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
     *
     * v10：根据认证用户身份校验是否可调用该工具所属工具组
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

        // v10：权限校验——根据当前用户身份判断是否可调用该工具
        String currentUserId = resolveCurrentUserId(arguments);
        String skillId = toolDefinitionService.getSkillIdByToolName(toolName);
        if (skillId == null) {
            return Result.error("工具不存在: " + toolName);
        }
        if (!skillService.canUserAccess(currentUserId, skillId)) {
            log.warn("工具调用被拒绝（无权访问工具组）: userId={}, toolName={}, skillId={}", currentUserId, toolName, skillId);
            return Result.error("无权调用该工具: " + toolName);
        }

        log.info("工具执行请求: tool={}, args={}, userId={}", toolName, arguments, currentUserId);

        Map<String, Object> result = toolExecuteService.execute(toolName, arguments);
        return Result.success(result);
    }

    /**
     * 获取所有启用的工具定义（供智能体发现工具）
     * 注意：此接口返回全部工具，不做用户过滤，仅用于管理后台。
     * 智能体应使用 /api/tools/definitions/user 获取按用户过滤的工具列表。
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
     *
     * v10：此接口返回所有 public 工具组，供未绑定用户的场景使用。
     *      智能体应优先调用 /api/tools/groups/user 获取按用户过滤的目录。
     */
    @GetMapping("/groups")
    public Result<List<Map<String, Object>>> getSkillGroups() {
        List<Skill> skills = skillService.getAccessibleSkillsWithToolCount(null);
        List<Map<String, Object>> groups = skills.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("name", s.getName());
                    g.put("display_name", s.getDisplayName());
                    g.put("description", s.getDescription());
                    g.put("category", s.getCategory());
                    g.put("visibility", s.getVisibility());
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
     *
     * v10：此接口仅校验 public 工具组。智能体应优先调用 /api/tools/group/{skillName}/user 获取按用户过滤的工具。
     */
    @GetMapping("/group/{skillName}")
    public Result<List<Map<String, Object>>> getGroupTools(
            @PathVariable String skillName,
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        // 仅返回 public 工具组的工具（向后兼容）
        List<Map<String, Object>> tools = toolDefinitionService.getAccessibleToolDefinitionsForGroup(null, skillName, baseUrl);
        return Result.success(tools);
    }

    // ===== v10 新增：用户维度的 skills 暴露接口 =====

    /**
     * 获取当前用户可访问的工具组目录
     * 智能体在初始化时应调用此接口，传入用户身份（通过 user_id 参数或 JWT/API Key 认证）
     *
     * 返回：public 工具组 + 当前用户已绑定的 private 工具组
     */
    @GetMapping("/groups/user")
    public Result<List<Map<String, Object>>> getSkillGroupsForUser(
            @RequestParam(value = "user_id", required = false) String userIdParam) {
        String userId = resolveUserIdFromAuthOrParam(userIdParam);
        List<Skill> skills = skillService.getAccessibleSkillsWithToolCount(userId);
        List<Map<String, Object>> groups = skills.stream()
                .filter(s -> "active".equals(s.getStatus()))
                .map(s -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("name", s.getName());
                    g.put("display_name", s.getDisplayName());
                    g.put("description", s.getDescription());
                    g.put("category", s.getCategory());
                    g.put("visibility", s.getVisibility());
                    g.put("tool_count", s.getToolCount());
                    g.put("bound", Boolean.TRUE.equals(s.getBound()));
                    return g;
                })
                .collect(Collectors.toList());
        return Result.success(groups);
    }

    /**
     * 获取当前用户可访问的所有工具定义（LLM function_call 格式）
     * 智能体可直接使用此列表进行 function calling
     */
    @GetMapping("/definitions/user")
    public Result<List<Map<String, Object>>> getToolDefinitionsForUser(
            @RequestParam(value = "user_id", required = false) String userIdParam,
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        String userId = resolveUserIdFromAuthOrParam(userIdParam);
        return Result.success(toolDefinitionService.getAccessibleToolDefinitionsForUser(userId, baseUrl));
    }

    /**
     * 获取指定工具组的工具详情（按用户身份过滤）
     * 智能体选择一个工具组后，调用此端点获取该组内所有工具的完整定义
     * 若用户无权访问该工具组（private 且未绑定），返回空列表
     */
    @GetMapping("/group/{skillName}/user")
    public Result<List<Map<String, Object>>> getGroupToolsForUser(
            @PathVariable String skillName,
            @RequestParam(value = "user_id", required = false) String userIdParam,
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        String userId = resolveUserIdFromAuthOrParam(userIdParam);
        List<Map<String, Object>> tools = toolDefinitionService.getAccessibleToolDefinitionsForGroup(userId, skillName, baseUrl);
        return Result.success(tools);
    }

    // ===== 内部工具方法 =====

    /**
     * 解析当前用户ID：
     * 1. 优先从 SecurityContext 中获取（JWT/API Key 认证）
     * 2. 其次从请求参数 user_id 获取（智能体调用时传入）
     * 3. 兜底从 arguments.user_id 获取
     */
    private String resolveCurrentUserId(Map<String, Object> arguments) {
        String userId = resolveUserIdFromAuthOrParam(null);
        if (userId != null) return userId;
        if (arguments != null) {
            Object argUserId = arguments.get("user_id");
            if (argUserId != null) return String.valueOf(argUserId);
            Object argAgentId = arguments.get("agent_id");
            if (argAgentId != null) return String.valueOf(argAgentId);
        }
        return null;
    }

    /**
     * 从认证上下文或请求参数解析用户ID
     */
    private String resolveUserIdFromAuthOrParam(String userIdParam) {
        // 1. 请求参数优先（智能体显式传入）
        if (userIdParam != null && !userIdParam.isBlank()) {
            return userIdParam;
        }
        // 2. 从认证上下文获取
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) return String.valueOf(userId);
        }
        if (auth != null && auth.getName() != null && auth.getName().startsWith("apikey-user-")) {
            // ApiKeyAuthenticationFilter 设置的 principal 格式：apikey-user-{userId}
            return auth.getName().substring("apikey-user-".length());
        }
        return null;
    }
}
