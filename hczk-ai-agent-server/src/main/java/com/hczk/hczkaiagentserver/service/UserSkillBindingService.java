package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.UserSkillBinding;

import java.util.List;

/**
 * 用户与私有工具组绑定服务接口
 */
public interface UserSkillBindingService {

    /** 绑定工具组到用户（若已存在则启用） */
    UserSkillBinding bind(String userId, String skillId);

    /** 解除绑定 */
    void unbind(String userId, String skillId);

    /** 切换绑定启用状态 */
    UserSkillBinding toggle(String userId, String skillId, boolean enabled);

    /** 查询用户绑定的所有工具组ID（仅启用的） */
    List<String> getBoundSkillIds(String userId);

    /** 查询用户绑定的所有绑定记录 */
    List<UserSkillBinding> getBindingsByUserId(String userId);

    /** 查询某个工具组绑定的所有用户ID（仅启用的） */
    List<String> getBoundUserIds(String skillId);

    /** 判断用户是否可访问指定工具组（public 或 已绑定且启用） */
    boolean canAccess(String userId, String skillId);
}
