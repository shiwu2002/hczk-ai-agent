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

/**
 * 智能体服务实现类
 *
 * 提供智能体的增删改查功能。
 * 支持三种智能体类型：MODEL（绑定模型）、SKILL（绑定Skill配置包）、ENDPOINT（转发外部地址）
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    /** 智能体数据访问 */
    private final AgentMapper agentMapper;

    /**
     * 获取所有智能体列表
     *
     * @return 智能体列表
     */
    @Override
    public List<Agent> getAllAgents() {
        return agentMapper.selectList(null);
    }

    /**
     * 根据用户ID获取智能体列表
     *
     * @param userId 用户ID
     * @return 该用户创建的智能体列表
     */
    @Override
    public List<Agent> getAgentsByUserId(Long userId) {
        return agentMapper.selectList(new LambdaQueryWrapper<Agent>().eq(Agent::getUserId, userId));
    }

    /**
     * 根据ID获取智能体
     *
     * @param id 智能体ID
     * @return 智能体实体
     * @throws RuntimeException 智能体不存在
     */
    @Override
    public Agent getAgentById(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        return agent;
    }

    /**
     * 创建智能体
     *
     * 校验规则：
     * - userId和name必填
     * - 默认启用（status=0），防止Jackson反序列化覆盖Java默认值
     * - 默认类型为MODEL
     * - MODEL类型必须绑定modelId
     * - SKILL类型必须绑定skillId
     * - ENDPOINT类型必须配置endpoint地址
     *
     * @param agent 智能体实体
     * @return 创建后的智能体（含自增id）
     * @throws IllegalArgumentException 参数校验失败
     */
    @Override
    @Transactional
    public Agent createAgent(Agent agent) {
        if (agent.getUserId() == null) {
            throw new IllegalArgumentException("user_id 不能为空");
        }
        if (agent.getName() == null || agent.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("智能体名称不能为空");
        }

        // 确保新建智能体默认启用
        if (agent.getStatus() == null) {
            agent.setStatus(AgentStatus.ACTIVE);
        }

        // 默认类型为MODEL
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

    /**
     * 更新智能体配置
     *
     * 根据智能体类型更新对应字段，其他类型字段置空
     * 不更新status字段，状态变更通过toggleStatus接口操作
     *
     * @param id    智能体ID
     * @param agent 更新数据
     * @return 更新后的智能体
     */
    @Override
    @Transactional
    public Agent updateAgent(Long id, Agent agent) {
        Agent existing = getAgentById(id);
        existing.setName(agent.getName());
        existing.setDescription(agent.getDescription());
        existing.setAgentType(agent.getAgentType());

        // 根据类型更新对应字段，其他类型字段置空
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

    /**
     * 删除智能体
     *
     * @param id 智能体ID
     */
    @Override
    @Transactional
    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
    }

    /**
     * 切换智能体启停状态
     *
     * ACTIVE(0) ↔ INACTIVE(1)
     *
     * @param id 智能体ID
     * @return 更新后的智能体
     */
    @Override
    @Transactional
    public Agent toggleStatus(Long id) {
        Agent agent = getAgentById(id);
        agent.setStatus(agent.getStatus() == AgentStatus.ACTIVE ? AgentStatus.INACTIVE : AgentStatus.ACTIVE);
        agentMapper.updateById(agent);
        return agent;
    }
}
