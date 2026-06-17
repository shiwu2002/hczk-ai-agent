package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.entity.ToolDefinition;
import com.hczk.hczkaiagentserver.entity.UserSkillBinding;
import com.hczk.hczkaiagentserver.service.AgentService;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import com.hczk.hczkaiagentserver.service.UserSkillBindingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform")
@CrossOrigin(origins = "*")
@Slf4j
public class PlatformController {

    private final SkillService skillService;
    private final ToolDefinitionService toolDefinitionService;
    private final MerchantAgentBindingService bindingService;
    private final AgentService agentService;
    private final UserSkillBindingService userSkillBindingService;

    public PlatformController(SkillService skillService, ToolDefinitionService toolDefinitionService,
                              MerchantAgentBindingService bindingService, AgentService agentService,
                              UserSkillBindingService userSkillBindingService) {
        this.skillService = skillService;
        this.toolDefinitionService = toolDefinitionService;
        this.bindingService = bindingService;
        this.agentService = agentService;
        this.userSkillBindingService = userSkillBindingService;
    }

    // ========== 智能体管理 ==========

    @PostMapping("/agents")
    public Result<Agent> createAgent(@RequestBody Agent agent) {
        return Result.success(agentService.createAgent(agent));
    }

    @GetMapping("/agents")
    public Result<List<Agent>> getAllAgents() {
        return Result.success(agentService.getAllAgents());
    }

    @GetMapping("/agents/{id}")
    public Result<Agent> getAgentById(@PathVariable Long id) {
        return Result.success(agentService.getAgentById(id));
    }

    @PutMapping("/agents/{id}")
    public Result<Agent> updateAgent(@PathVariable Long id, @RequestBody Agent agent) {
        return Result.success(agentService.updateAgent(id, agent));
    }

    @DeleteMapping("/agents/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        agentService.deleteAgent(id);
        return Result.success();
    }

    @PostMapping("/agents/{id}/toggle")
    public Result<Agent> toggleAgentStatus(@PathVariable Long id) {
        return Result.success(agentService.toggleStatus(id));
    }

    // ========== 工具组管理 ==========

    @PostMapping("/skills")
    public Result<Skill> createSkill(@RequestBody Skill skill) {
        try {
            return Result.success(skillService.createSkill(skill));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/skills")
    public Result<List<Skill>> getAllSkills() {
        return Result.success(skillService.getAllSkillsWithToolCount());
    }

    @GetMapping("/skills/{skillId}")
    public Result<Skill> getSkillById(@PathVariable String skillId) {
        Skill skill = skillService.getSkillById(skillId);
        if (skill == null) return Result.error("工具组不存在");
        return Result.success(skill);
    }

    @PutMapping("/skills/{skillId}")
    public Result<Skill> updateSkill(@PathVariable String skillId, @RequestBody Skill skill) {
        try {
            return Result.success(skillService.updateSkill(skillId, skill));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/skills/{skillId}")
    public Result<Void> deleteSkill(@PathVariable String skillId) {
        try {
            skillService.deleteSkill(skillId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/skills/{skillId}/toggle")
    public Result<Skill> toggleSkillStatus(@PathVariable String skillId) {
        try {
            return Result.success(skillService.toggleStatus(skillId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 获取工具组下的所有工具 */
    @GetMapping("/skills/{skillId}/tools")
    public Result<List<ToolDefinition>> getSkillTools(@PathVariable String skillId) {
        return Result.success(toolDefinitionService.getBySkillId(skillId));
    }

    // ========== 工具管理 ==========

    @PostMapping("/tools")
    public Result<ToolDefinition> createTool(@RequestBody ToolDefinition tool) {
        try {
            return Result.success(toolDefinitionService.create(tool));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/tools/{toolId}")
    public Result<ToolDefinition> updateTool(@PathVariable Long toolId, @RequestBody ToolDefinition tool) {
        try {
            return Result.success(toolDefinitionService.update(toolId, tool));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/tools/{toolId}")
    public Result<Void> deleteTool(@PathVariable Long toolId) {
        try {
            toolDefinitionService.delete(toolId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tools/{toolId}/toggle")
    public Result<ToolDefinition> toggleToolStatus(@PathVariable Long toolId) {
        try {
            return Result.success(toolDefinitionService.toggleStatus(toolId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /** 获取所有启用的工具定义（LLM 格式） */
    @GetMapping("/tools/definitions")
    public Result<List<Map<String, Object>>> getActiveToolDefinitions(
            @RequestHeader(value = "Host", required = false) String host) {
        String baseUrl = "http://" + (host != null ? host : "localhost:8080");
        return Result.success(toolDefinitionService.getActiveToolDefinitions(baseUrl));
    }

    // ========== 绑定管理 ==========

    @PostMapping("/bindings")
    public Result<MerchantAgentBinding> createBinding(@RequestBody MerchantAgentBinding binding) {
        return Result.success(bindingService.createBinding(binding));
    }

    @GetMapping("/bindings")
    public Result<List<MerchantAgentBinding>> getAllBindings() {
        return Result.success(bindingService.getAllBindings());
    }

    @GetMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> getBindingByUserId(@PathVariable String userId) {
        return bindingService.findByUserId(userId)
                .map(Result::success)
                .orElse(Result.error("绑定不存在"));
    }

    @PutMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> updateBinding(@PathVariable String userId, @RequestBody MerchantAgentBinding binding) {
        return Result.success(bindingService.updateBinding(userId, binding));
    }

    @DeleteMapping("/bindings/{userId}")
    public Result<Void> deleteBinding(@PathVariable String userId) {
        bindingService.deleteBinding(userId);
        return Result.success();
    }

    @PatchMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> toggleBinding(@PathVariable String userId, @RequestParam boolean enabled) {
        return Result.success(bindingService.toggleBinding(userId, enabled));
    }

    // ========== v10 新增：Skill 可见性与用户绑定管理 ==========

    /**
     * 设置工具组可见性（public / private）
     */
    @PutMapping("/skills/{skillId}/visibility")
    public Result<Skill> setSkillVisibility(@PathVariable String skillId, @RequestParam String visibility) {
        if (!"public".equals(visibility) && !"private".equals(visibility)) {
            return Result.error("visibility 取值仅支持 public / private");
        }
        try {
            Skill patch = new Skill();
            patch.setVisibility(visibility);
            return Result.success(skillService.updateSkill(skillId, patch));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 绑定工具组到用户
     * 请求体：{ "user_id": "xxx", "skill_id": "xxx" }
     */
    @PostMapping("/skill-bindings")
    public Result<UserSkillBinding> bindSkillToUser(@RequestBody Map<String, String> body) {
        String userId = body.get("user_id");
        String skillId = body.get("skill_id");
        if (userId == null || userId.isBlank()) return Result.error("缺少 user_id");
        if (skillId == null || skillId.isBlank()) return Result.error("缺少 skill_id");
        try {
            return Result.success(userSkillBindingService.bind(userId, skillId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解除用户与工具组的绑定
     */
    @DeleteMapping("/skill-bindings")
    public Result<Void> unbindSkillFromUser(@RequestParam String userId, @RequestParam String skillId) {
        try {
            userSkillBindingService.unbind(userId, skillId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 切换用户工具组绑定的启用状态
     */
    @PatchMapping("/skill-bindings")
    public Result<UserSkillBinding> toggleSkillBinding(@RequestParam String userId,
                                                       @RequestParam String skillId,
                                                       @RequestParam boolean enabled) {
        try {
            return Result.success(userSkillBindingService.toggle(userId, skillId, enabled));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询指定用户绑定的所有工具组绑定记录
     */
    @GetMapping("/skill-bindings/user/{userId}")
    public Result<List<UserSkillBinding>> getBindingsByUser(@PathVariable String userId) {
        return Result.success(userSkillBindingService.getBindingsByUserId(userId));
    }

    /**
     * 查询指定工具组绑定的所有用户ID
     */
    @GetMapping("/skill-bindings/skill/{skillId}")
    public Result<List<String>> getBoundUsersBySkill(@PathVariable String skillId) {
        return Result.success(userSkillBindingService.getBoundUserIds(skillId));
    }
}
