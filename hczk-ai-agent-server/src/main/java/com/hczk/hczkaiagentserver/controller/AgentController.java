package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public Result<List<Agent>> getAllAgents() {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            return Result.error(401, "未认证，无法识别用户身份");
        }
        // 普通用户仅能查看自己的智能体；ADMIN 可查看全部
        if (isAdmin()) {
            return Result.success(agentService.getAllAgents());
        }
        return Result.success(agentService.getAgentsByUserId(currentUserId));
    }

    @GetMapping("/user/{userId}")
    public Result<List<Agent>> getAgentsByUserId(@PathVariable String userId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            return Result.error(401, "未认证，无法识别用户身份");
        }
        // 普通用户只能查询自己的智能体
        if (!isAdmin() && !currentUserId.equals(userId)) {
            log.warn("查询他人智能体被拒绝: currentUserId={}, targetUserId={}", currentUserId, userId);
            return Result.error(403, "无权查询其他用户的智能体");
        }
        return Result.success(agentService.getAgentsByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<Agent> getAgentById(@PathVariable Long id) {
        Result<Void> denied = checkOwnership(id);
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        return Result.success(agentService.getAgentById(id));
    }

    @PostMapping
    public Result<Agent> createAgent(@RequestBody Agent agent) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            return Result.error(401, "未认证，无法识别用户身份");
        }
        // 强制以认证用户身份设置 userId，防止伪造
        if (!isAdmin()) {
            agent.setUserId(currentUserId);
        } else if (agent.getUserId() == null || agent.getUserId().isBlank()) {
            // ADMIN 未指定 userId 时默认为自己
            agent.setUserId(currentUserId);
        }
        return Result.success(agentService.createAgent(agent));
    }

    @PutMapping("/{id}")
    public Result<Agent> updateAgent(@PathVariable Long id, @RequestBody Agent agent) {
        Result<Void> denied = checkOwnership(id);
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        // 防止普通用户通过更新篡改 userId 把智能体转交给他人
        String currentUserId = getCurrentUserId();
        if (!isAdmin()) {
            agent.setUserId(currentUserId);
        }
        return Result.success(agentService.updateAgent(id, agent));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        Result<Void> denied = checkOwnership(id);
        if (denied != null) return denied;
        agentService.deleteAgent(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Agent> toggleStatus(@PathVariable Long id) {
        Result<Void> denied = checkOwnership(id);
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        return Result.success(agentService.toggleStatus(id));
    }

    /**
     * 批量检测所有智能体的健康状态
     */
    @GetMapping("/health")
    public Result<Map<Long, Map<String, Object>>> checkAllHealth() {
        // 健康检测为公开接口（SecurityConfig 中放行），保持原行为
        return Result.success(agentService.checkAllAgentsHealth());
    }

    /**
     * 检测单个智能体的健康状态
     */
    @GetMapping("/{id}/health")
    public Result<Map<String, Object>> checkHealth(@PathVariable Long id) {
        // 健康检测为公开接口（SecurityConfig 中放行），保持原行为
        return Result.success(agentService.checkAgentHealth(id));
    }

    /**
     * 获取智能体元信息（代理调用 infoEndpoint）
     */
    @GetMapping("/{id}/info")
    public Result<Map<String, Object>> getAgentInfo(@PathVariable Long id) {
        Result<Void> denied = checkOwnership(id);
        if (denied != null) return Result.error(denied.getCode(), denied.getMessage());
        return Result.success(agentService.getAgentInfo(id));
    }

    // ==================== 私有方法 ====================

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId != null) return String.valueOf(userId);
        }
        if (auth != null && auth.getName() != null && auth.getName().startsWith("apikey-user-")) {
            return auth.getName().substring("apikey-user-".length());
        }
        return null;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) return true;
        }
        return false;
    }

    /**
     * 校验当前用户对智能体的所有权
     * @return null 校验通过；否则返回错误 Result
     */
    private Result<Void> checkOwnership(Long agentId) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            return Result.error(401, "未认证，无法识别用户身份");
        }
        if (isAdmin()) {
            return null;
        }
        Agent existing = agentService.getAgentById(agentId);
        if (existing == null) {
            return Result.error("智能体不存在");
        }
        if (existing.getUserId() == null || !currentUserId.equals(existing.getUserId())) {
            log.warn("智能体越权访问被拒绝: currentUserId={}, agentId={}, ownerUserId={}",
                    currentUserId, agentId, existing.getUserId());
            return Result.error(403, "无权操作该智能体");
        }
        return null;
    }
}
