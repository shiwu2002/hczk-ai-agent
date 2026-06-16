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
}
