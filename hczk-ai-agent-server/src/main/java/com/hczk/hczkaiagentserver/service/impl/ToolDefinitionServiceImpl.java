package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.entity.ToolDefinition;
import com.hczk.hczkaiagentserver.mapper.SkillMapper;
import com.hczk.hczkaiagentserver.mapper.ToolDefinitionMapper;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolDefinitionServiceImpl implements ToolDefinitionService {

    private final ToolDefinitionMapper toolMapper;
    private final SkillMapper skillMapper;
    private final SkillService skillService;
    private final RedisCacheService redisCache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public ToolDefinition create(ToolDefinition tool) {
        if (tool.getType() == null) tool.setType("api");
        if (tool.getStatus() == null) tool.setStatus("active");
        toolMapper.insert(tool);
        redisCache.invalidateByPrefix("tools:");
        redisCache.deleteKey("skills:all");
        log.info("创建工具: id={}, name={}, skillId={}", tool.getId(), tool.getName(), tool.getSkillId());
        return tool;
    }

    @Override
    @Transactional
    public ToolDefinition update(Long id, ToolDefinition tool) {
        ToolDefinition existing = toolMapper.selectById(id);
        if (existing == null) throw new RuntimeException("工具不存在: " + id);
        tool.setId(id);
        toolMapper.updateById(tool);
        redisCache.invalidateByPrefix("tools:");
        redisCache.deleteKey("skills:all");
        log.info("更新工具: id={}", id);
        return toolMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ToolDefinition tool = toolMapper.selectById(id);
        if (tool == null) throw new RuntimeException("工具不存在: " + id);
        toolMapper.deleteById(id);
        redisCache.invalidateByPrefix("tools:");
        redisCache.deleteKey("skills:all");
        log.info("删除工具: id={}, name={}, type={}", id, tool.getName(), tool.getType());
    }

    @Override
    public List<ToolDefinition> getBySkillId(String skillId) {
        String cacheKey = RedisCacheService.toolsKey("skill:" + skillId);
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ToolDefinition.class));
            } catch (Exception e) {
                log.debug("工具缓存反序列化失败，回源查询: skillId={}", skillId);
            }
        }
        QueryWrapper<ToolDefinition> qw = new QueryWrapper<>();
        qw.eq("skill_id", skillId).orderByAsc("id");
        List<ToolDefinition> list = toolMapper.selectList(qw);
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, objectMapper.writeValueAsString(list));
        } catch (Exception ignored) {}
        return list;
    }

    @Override
    public List<ToolDefinition> getActiveBySkillId(String skillId) {
        String cacheKey = RedisCacheService.toolsKey("active_skill:" + skillId);
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ToolDefinition.class));
            } catch (Exception e) {
                log.debug("缓存反序列化失败，回源: skillId={}", skillId);
            }
        }
        QueryWrapper<ToolDefinition> qw = new QueryWrapper<>();
        qw.eq("skill_id", skillId).eq("status", "active").orderByAsc("id");
        List<ToolDefinition> list = toolMapper.selectList(qw);
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, objectMapper.writeValueAsString(list));
        } catch (Exception ignored) {}
        return list;
    }

    @Override
    public List<ToolDefinition> getAllActive() {
        QueryWrapper<ToolDefinition> qw = new QueryWrapper<>();
        qw.eq("status", "active").orderByAsc("skill_id").orderByAsc("id");
        return toolMapper.selectList(qw);
    }

    @Override
    @Transactional
    public ToolDefinition toggleStatus(Long id) {
        ToolDefinition tool = toolMapper.selectById(id);
        if (tool == null) throw new RuntimeException("工具不存在: " + id);
        tool.setStatus("active".equals(tool.getStatus()) ? "inactive" : "active");
        toolMapper.updateById(tool);
        redisCache.invalidateByPrefix("tools:");
        redisCache.deleteKey("skills:all");
        return tool;
    }

    @Override
    public List<Map<String, Object>> getActiveToolDefinitions(String platformBaseUrl) {
        return convertToolsToLLMFormat(getAllActive(), platformBaseUrl);
    }

    @Override
    public List<Map<String, Object>> getActiveToolDefinitionsForGroup(String skillName, String platformBaseUrl) {
        String cacheKey = RedisCacheService.toolsKey("llm_group:" + skillName);
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                log.debug("LLM工具缓存反序列化失败，回源: skillName={}", skillName);
            }
        }

        QueryWrapper<Skill> sq = new QueryWrapper<>();
        sq.eq("name", skillName).eq("status", "active");
        Skill skill = skillMapper.selectOne(sq);
        if (skill == null) return List.of();

        List<Map<String, Object>> tools = convertToolsToLLMFormat(getActiveBySkillId(skill.getId()), platformBaseUrl);
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, objectMapper.writeValueAsString(tools));
        } catch (Exception ignored) {}
        return tools;
    }

    // ===== v10 新增：用户可见性相关 =====

    @Override
    public List<Map<String, Object>> getAccessibleToolDefinitionsForUser(String userId, String platformBaseUrl) {
        // 获取用户可访问的所有 skill（public + bound private）
        List<Skill> accessible = skillService.getAccessibleSkillsWithToolCount(userId);
        List<ToolDefinition> tools = new ArrayList<>();
        for (Skill skill : accessible) {
            if (!"active".equals(skill.getStatus())) continue;
            tools.addAll(getActiveBySkillId(skill.getId()));
        }
        return convertToolsToLLMFormat(tools, platformBaseUrl);
    }

    @Override
    public List<Map<String, Object>> getAccessibleToolDefinitionsForGroup(String userId, String skillName, String platformBaseUrl) {
        Skill skill = skillService.getSkillByName(skillName);
        if (skill == null) return List.of();
        if (!"active".equals(skill.getStatus())) return List.of();
        // 权限校验
        if (!skillService.canUserAccess(userId, skill.getId())) {
            log.warn("用户无权访问工具组: userId={}, skillName={}, skillId={}", userId, skillName, skill.getId());
            return List.of();
        }
        return convertToolsToLLMFormat(getActiveBySkillId(skill.getId()), platformBaseUrl);
    }

    @Override
    public String getSkillIdByToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) return null;
        QueryWrapper<ToolDefinition> qw = new QueryWrapper<>();
        qw.eq("name", toolName).last("LIMIT 1");
        ToolDefinition tool = toolMapper.selectOne(qw);
        return tool != null ? tool.getSkillId() : null;
    }

    public List<String> validateRequiredParams(String toolName, Map<String, Object> arguments) {
        List<String> missing = new ArrayList<>();
        QueryWrapper<ToolDefinition> qw = new QueryWrapper<>();
        qw.eq("name", toolName).last("LIMIT 1");
        ToolDefinition tool = toolMapper.selectOne(qw);
        if (tool == null || tool.getInputSchema() == null || tool.getInputSchema().isBlank()) {
            return missing;
        }
        try {
            Map<String, Object> schema = objectMapper.readValue(tool.getInputSchema(), Map.class);
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            if (required != null) {
                for (String param : required) {
                    if (arguments == null || !arguments.containsKey(param) || arguments.get(param) == null
                            || String.valueOf(arguments.get(param)).isBlank()) {
                        missing.add(param);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析工具 {} 的 input_schema 失败: {}", toolName, e.getMessage());
        }
        return missing;
    }

    /** 将 ToolDefinition 列表转换为 LLM function_call 格式 */
    private List<Map<String, Object>> convertToolsToLLMFormat(List<ToolDefinition> toolsList, String platformBaseUrl) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition tool : toolsList) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");

            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());

            if (tool.getInputSchema() != null && !tool.getInputSchema().trim().isEmpty()) {
                try {
                    function.put("parameters", objectMapper.readValue(tool.getInputSchema(), Object.class));
                } catch (Exception e) {
                    log.warn("工具 {} input_schema 解析失败: {}", tool.getName(), e.getMessage());
                }
            }

            def.put("function", function);

            String executionEndpoint;
            if ("api".equals(tool.getType()) && tool.getEndpoint() != null && !tool.getEndpoint().isBlank()) {
                executionEndpoint = tool.getEndpoint();
            } else {
                executionEndpoint = platformBaseUrl + "/api/tools/execute";
            }
            def.put("endpoint", executionEndpoint);
            result.add(def);
        }
        return result;
    }
}
