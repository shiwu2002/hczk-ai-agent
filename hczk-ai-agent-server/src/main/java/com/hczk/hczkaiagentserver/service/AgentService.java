package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.Agent;

import java.util.List;
import java.util.Map;

public interface AgentService {
    List<Agent> getAllAgents();
    List<Agent> getAgentsByUserId(Long userId);
    Agent getAgentById(Long id);
    Agent createAgent(Agent agent);
    Agent updateAgent(Long id, Agent agent);
    void deleteAgent(Long id);
    Agent toggleStatus(Long id);

    /**
     * 批量检测所有智能体的健康状态
     * @return key=agentId, value=健康状态信息
     */
    Map<Long, Map<String, Object>> checkAllAgentsHealth();

    /**
     * 检测单个智能体的健康状态
     */
    Map<String, Object> checkAgentHealth(Long id);

    /**
     * 获取智能体元信息（通过 infoEndpoint 代理获取）
     */
    Map<String, Object> getAgentInfo(Long id);
}
