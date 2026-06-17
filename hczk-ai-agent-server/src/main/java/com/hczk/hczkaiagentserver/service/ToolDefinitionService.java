package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface ToolDefinitionService {
    ToolDefinition create(ToolDefinition tool);
    ToolDefinition update(Long id, ToolDefinition tool);
    void delete(Long id);
    List<ToolDefinition> getBySkillId(String skillId);
    List<ToolDefinition> getActiveBySkillId(String skillId);
    List<ToolDefinition> getAllActive();
    ToolDefinition toggleStatus(Long id);

    /** 将所有启用的工具转换为 LLM function_call tools 格式 */
    List<Map<String, Object>> getActiveToolDefinitions(String platformBaseUrl);

    /** 获取指定工具组的工具定义（LLM 格式） */
    List<Map<String, Object>> getActiveToolDefinitionsForGroup(String skillName, String platformBaseUrl);

    // ===== v10 新增：用户可见性相关 =====

    /**
     * 获取指定用户可访问的所有工具定义（LLM 格式）
     * 仅返回 public 工具组 + 用户已绑定的 private 工具组下的工具
     *
     * @param userId          用户ID，为 null 时仅返回 public
     * @param platformBaseUrl 平台基础URL
     */
    List<Map<String, Object>> getAccessibleToolDefinitionsForUser(String userId, String platformBaseUrl);

    /**
     * 获取指定工具组的工具定义（LLM 格式），并校验用户访问权限
     *
     * @param userId          用户ID，为 null 时仅允许 public
     * @param skillName       工具组 name
     * @param platformBaseUrl 平台基础URL
     * @return 工具定义列表；若用户无权访问返回空列表
     */
    List<Map<String, Object>> getAccessibleToolDefinitionsForGroup(String userId, String skillName, String platformBaseUrl);

    /**
     * 根据工具名查找所属工具组ID
     */
    String getSkillIdByToolName(String toolName);
}
