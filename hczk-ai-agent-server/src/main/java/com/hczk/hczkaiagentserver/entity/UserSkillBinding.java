package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与私有工具组（Skill）绑定关系实体
 * 仅 visibility=private 的 Skill 需要通过此表绑定用户后才能被对应智能体调用
 */
@TableName("user_skill_binding")
@Data
public class UserSkillBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（users.user_id 雪花ID字符串） */
    @TableField("user_id")
    private String userId;

    /** 工具组ID（skill.id） */
    @TableField("skill_id")
    private String skillId;

    /** 是否启用：true启用 / false禁用 */
    private Boolean enabled = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
