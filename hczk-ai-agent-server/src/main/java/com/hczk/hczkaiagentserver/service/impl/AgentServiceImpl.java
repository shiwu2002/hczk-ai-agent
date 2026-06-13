package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import com.hczk.hczkaiagentserver.repository.AgentRepository;
import com.hczk.hczkaiagentserver.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;

    @Override
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    @Override
    public List<Agent> getAgentsByUserId(Long userId) {
        return agentRepository.findByUserId(userId);
    }

    @Override
    public Agent getAgentById(Long id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("智能体不存在"));
    }

    @Override
    @Transactional
    public Agent createAgent(Agent agent) {
        return agentRepository.save(agent);
    }

    @Override
    @Transactional
    public Agent updateAgent(Long id, Agent agent) {
        Agent existing = getAgentById(id);
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setModel(agent.getModel());
        existing.setAgentType(agent.getAgentType());
        return agentRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteAgent(Long id) {
        agentRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Agent toggleStatus(Long id) {
        Agent agent = getAgentById(id);
        agent.setStatus(agent.getStatus() == AgentStatus.ACTIVE ? AgentStatus.INACTIVE : AgentStatus.ACTIVE);
        return agentRepository.save(agent);
    }
}
