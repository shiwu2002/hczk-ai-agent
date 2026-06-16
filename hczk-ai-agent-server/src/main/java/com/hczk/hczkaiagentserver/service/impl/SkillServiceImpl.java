package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.mapper.SkillMapper;
import com.hczk.hczkaiagentserver.mapper.ToolDefinitionMapper;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillMapper skillMapper;
    private final ToolDefinitionMapper toolDefMapper;
    private final ToolDefinitionService toolDefinitionService;
    private final RedisCacheService redisCache;
    private final ObjectMapper objectMapper;
    private static final String CACHE_KEY = "skills:all";

    @Override
    @Transactional
    public Skill createSkill(Skill skill) {
        if (skill.getStatus() == null) skill.setStatus("active");
        skillMapper.insert(skill);
        redisCache.deleteKey(CACHE_KEY);
        log.info("创建工具组: id={}, name={}", skill.getId(), skill.getName());
        return skill;
    }

    @Override
    @Transactional
    public Skill updateSkill(String skillId, Skill skill) {
        Skill existing = skillMapper.selectById(skillId);
        if (existing == null) throw new RuntimeException("工具组不存在: " + skillId);
        skill.setId(skillId);
        skillMapper.updateById(skill);
        redisCache.deleteKey(CACHE_KEY);
        log.info("更新工具组: id={}", skillId);
        return skillMapper.selectById(skillId);
    }

    @Override
    @Transactional
    public void deleteSkill(String skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new RuntimeException("工具组不存在: " + skillId);
        // 级联删除工具：由 DB 外键 ON DELETE CASCADE 处理
        skillMapper.deleteById(skillId);
        redisCache.deleteKey(CACHE_KEY);
        log.info("删除工具组: id={}", skillId);
    }

    @Override
    public Skill getSkillById(String skillId) {
        return skillMapper.selectById(skillId);
    }

    @Override
    public List<Skill> getAllSkills() {
        String cached = redisCache.getCachedJson(CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, objectMapper.getTypeFactory().constructCollectionType(List.class, Skill.class));
            } catch (Exception e) { log.debug("skills缓存反序列化失败，回源"); }
        }
        QueryWrapper<Skill> qw = new QueryWrapper<>();
        qw.orderByAsc("category").orderByAsc("name");
        List<Skill> list = skillMapper.selectList(qw);
        try { redisCache.cacheJsonWithRandomTTL(CACHE_KEY, objectMapper.writeValueAsString(list)); } catch (Exception ignored) {}
        return list;
    }

    @Override
    public List<Skill> getAllSkillsWithToolCount() {
        List<Skill> skills = getAllSkills();
        for (Skill skill : skills) {
            // 用 COUNT 查询替代全量加载，避免 N+1 且不加载工具详情
            QueryWrapper<com.hczk.hczkaiagentserver.entity.ToolDefinition> cqw = new QueryWrapper<>();
            cqw.eq("skill_id", skill.getId());
            skill.setToolCount(toolDefMapper.selectCount(cqw).intValue());
            // 不设置 skill.setTools()，前端按需请求 GET /platform/skills/{skillId}/tools
        }
        return skills;
    }

    @Override
    @Transactional
    public Skill toggleStatus(String skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new RuntimeException("工具组不存在: " + skillId);
        skill.setStatus("active".equals(skill.getStatus()) ? "inactive" : "active");
        skillMapper.updateById(skill);
        return skill;
    }
}
