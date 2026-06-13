package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import com.hczk.hczkaiagentserver.mapper.AgentMapper;
import com.hczk.hczkaiagentserver.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;

    @Override
    public List<Agent> getAllAgents() {
        return agentMapper.selectList(null);
    }

    @Override
    public List<Agent> getAgentsByUserId(Long userId) {
        return agentMapper.selectList(new LambdaQueryWrapper<Agent>().eq(Agent::getUserId, userId));
    }

    @Override
    public Agent getAgentById(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        return agent;
    }

    @Override
    @Transactional
    public Agent createAgent(Agent agent) {
        agentMapper.insert(agent);
        return agent;
    }

    @Override
    @Transactional
    public Agent updateAgent(Long id, Agent agent) {
        Agent existing = getAgentById(id);
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setModelId(agent.getModelId());
        existing.setAgentType(agent.getAgentType());
        agentMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Agent toggleStatus(Long id) {
        Agent agent = getAgentById(id);
        agent.setStatus(agent.getStatus() == AgentStatus.ACTIVE ? AgentStatus.INACTIVE : AgentStatus.ACTIVE);
        agentMapper.updateById(agent);
        return agent;
    }
}
