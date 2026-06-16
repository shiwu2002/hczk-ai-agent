package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public Result<List<Agent>> getAllAgents() {
        return Result.success(agentService.getAllAgents());
    }

    @GetMapping("/user/{userId}")
    public Result<List<Agent>> getAgentsByUserId(@PathVariable String userId) {
        return Result.success(agentService.getAgentsByUserId(userId));
    }

    @GetMapping("/{id}")
    public Result<Agent> getAgentById(@PathVariable Long id) {
        return Result.success(agentService.getAgentById(id));
    }

    @PostMapping
    public Result<Agent> createAgent(@RequestBody Agent agent) {
        return Result.success(agentService.createAgent(agent));
    }

    @PutMapping("/{id}")
    public Result<Agent> updateAgent(@PathVariable Long id, @RequestBody Agent agent) {
        return Result.success(agentService.updateAgent(id, agent));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Agent> toggleStatus(@PathVariable Long id) {
        return Result.success(agentService.toggleStatus(id));
    }

    /**
     * 批量检测所有智能体的健康状态
     */
    @GetMapping("/health")
    public Result<Map<Long, Map<String, Object>>> checkAllHealth() {
        return Result.success(agentService.checkAllAgentsHealth());
    }

    /**
     * 检测单个智能体的健康状态
     */
    @GetMapping("/{id}/health")
    public Result<Map<String, Object>> checkHealth(@PathVariable Long id) {
        return Result.success(agentService.checkAgentHealth(id));
    }

    /**
     * 获取智能体元信息（代理调用 infoEndpoint）
     */
    @GetMapping("/{id}/info")
    public Result<Map<String, Object>> getAgentInfo(@PathVariable Long id) {
        return Result.success(agentService.getAgentInfo(id));
    }
}
