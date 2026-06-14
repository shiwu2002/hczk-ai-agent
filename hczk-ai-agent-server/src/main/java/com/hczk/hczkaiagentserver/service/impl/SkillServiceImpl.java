package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.mapper.SkillMapper;
import com.hczk.hczkaiagentserver.service.SkillService;
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

    @Override
    @Transactional
    public Skill createSkill(Skill skill) {
        skillMapper.insert(skill);
        log.info("创建 Skill: id={}, name={}", skill.getId(), skill.getName());
        return skill;
    }

    @Override
    @Transactional
    public Skill updateSkill(String skillId, Skill skill) {
        Skill existing = skillMapper.selectById(skillId);
        if (existing == null) {
            throw new RuntimeException("Skill 不存在: " + skillId);
        }
        skill.setId(skillId);
        skillMapper.updateById(skill);
        log.info("更新 Skill: id={}", skillId);
        return skill;
    }

    @Override
    @Transactional
    public void deleteSkill(String skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new RuntimeException("Skill 不存在: " + skillId);
        }
        skillMapper.deleteById(skillId);
        log.info("删除 Skill: id={}", skillId);
    }

    @Override
    public Skill getSkillById(String skillId) {
        return skillMapper.selectById(skillId);
    }

    @Override
    public List<Skill> getAllSkills() {
        return skillMapper.selectList(null);
    }
}