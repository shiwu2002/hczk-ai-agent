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
        if (agent.getUserId() == null) {
            throw new IllegalArgumentException("user_id 不能为空");
        }
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("智能体名称不能为空");
        }
        
        String agentType = agent.getAgentType();
        if (agentType == null) {
            agentType = "MODEL";
            agent.setAgentType(agentType);
        }
        
        // 根据类型验证必填字段
        switch (agentType) {
            case "MODEL":
                if (agent.getModelId() == null) {
                    throw new IllegalArgumentException("MODEL 类型智能体必须绑定模型");
                }
                break;
            case "SKILL":
                if (agent.getSkillId() == null || agent.getSkillId().trim().isEmpty()) {
                    throw new IllegalArgumentException("SKILL 类型智能体必须绑定 Skill");
                }
                break;
            case "ENDPOINT":
                if (agent.getEndpoint() == null || agent.getEndpoint().trim().isEmpty()) {
                    throw new IllegalArgumentException("ENDPOINT 类型智能体必须配置 Endpoint 地址");
                }
                break;
            default:
                throw new IllegalArgumentException("未知智能体类型: " + agentType);
        }
        
        agentMapper.insert(agent);
        return agent;
    }

    @Override
    @Transactional
    public Agent updateAgent(Long id, Agent agent) {
        Agent existing = getAgentById(id);
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setAgentType(agent.getAgentType());
        
        // 根据类型更新对应字段
        String agentType = agent.getAgentType();
        if ("MODEL".equals(agentType)) {
            existing.setModelId(agent.getModelId());
            existing.setSkillId(null);
            existing.setEndpoint(null);
            existing.setEndpointAuthHeader(null);
        } else if ("SKILL".equals(agentType)) {
            existing.setSkillId(agent.getSkillId());
            existing.setModelId(null);
            existing.setEndpoint(null);
            existing.setEndpointAuthHeader(null);
        } else if ("ENDPOINT".equals(agentType)) {
            existing.setEndpoint(agent.getEndpoint());
            existing.setEndpointAuthHeader(agent.getEndpointAuthHeader());
            existing.setModelId(null);
            existing.setSkillId(null);
        }
        
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
