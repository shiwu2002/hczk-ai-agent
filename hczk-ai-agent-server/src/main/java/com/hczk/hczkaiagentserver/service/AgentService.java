package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.Agent;

import java.util.List;

public interface AgentService {
    List<Agent> getAllAgents();
    List<Agent> getAgentsByUserId(Long userId);
    Agent getAgentById(Long id);
    Agent createAgent(Agent agent);
    Agent updateAgent(Long id, Agent agent);
    void deleteAgent(Long id);
    Agent toggleStatus(Long id);
}
