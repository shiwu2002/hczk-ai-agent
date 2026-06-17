package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.Skill;

import java.util.List;

/**
 * 工具组服务接口（v5 重构：从"技能包"改为"工具组"）
 * v10 新增：基于用户可见性的查询能力
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

    // ===== v10 新增：用户可见性相关 =====

    /**
     * 获取指定用户可访问的工具组（含工具数量）
     * 规则：visibility=public 的全部 + visibility=private 且用户已绑定并启用的
     *
     * @param userId 用户ID（雪花ID字符串），为 null 时仅返回 public
     */
    List<Skill> getAccessibleSkillsWithToolCount(String userId);

    /**
     * 判断用户是否可访问指定工具组
     * 规则：public 直接可访问；private 需 user_skill_binding 中存在且 enabled=1
     *
     * @param userId  用户ID，为 null 时仅 public 可访问
     * @param skillId 工具组ID
     */
    boolean canUserAccess(String userId, String skillId);

    /**
     * 根据 name 获取工具组（用于智能体按 name 调用）
     */
    Skill getSkillByName(String skillName);
}
