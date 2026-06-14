package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.Skill;

import java.util.List;

public interface SkillService {
    Skill createSkill(Skill skill);
    Skill updateSkill(String skillId, Skill skill);
    void deleteSkill(String skillId);
    Skill getSkillById(String skillId);
    List<Skill> getAllSkills();
}