package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.Skill;

import java.util.List;

/**
 * 工具组服务接口（v5 重构：从"技能包"改为"工具组"）
 */
public interface SkillService {

    /** 创建工具组 */
    Skill createSkill(Skill skill);

    /** 更新工具组 */
    Skill updateSkill(String skillId, Skill skill);

    /** 删除工具组 */
    void deleteSkill(String skillId);

    /** 按ID获取工具组 */
    Skill getSkillById(String skillId);

    /** 获取所有工具组 */
    List<Skill> getAllSkills();

    /** 获取所有工具组（含工具数量和工具列表） */
    List<Skill> getAllSkillsWithToolCount();

    /** 切换工具组启用/禁用 */
    Skill toggleStatus(String skillId);
}
