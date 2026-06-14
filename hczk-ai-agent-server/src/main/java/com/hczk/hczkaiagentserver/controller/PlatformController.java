package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import com.hczk.hczkaiagentserver.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PlatformController {

    private final SkillService skillService;
    private final MerchantAgentBindingService bindingService;

    @PostMapping("/skills")
    public Result<Skill> createSkill(@RequestBody Skill skill) {
        return Result.success(skillService.createSkill(skill));
    }

    @GetMapping("/skills")
    public Result<List<Skill>> getAllSkills() {
        return Result.success(skillService.getAllSkills());
    }

    @GetMapping("/skills/{skillId}")
    public Result<Skill> getSkillById(@PathVariable String skillId) {
        Skill skill = skillService.getSkillById(skillId);
        if (skill == null) {
            return Result.error("Skill 不存在");
        }
        return Result.success(skill);
    }

    @PutMapping("/skills/{skillId}")
    public Result<Skill> updateSkill(@PathVariable String skillId, @RequestBody Skill skill) {
        return Result.success(skillService.updateSkill(skillId, skill));
    }

    @DeleteMapping("/skills/{skillId}")
    public Result<Void> deleteSkill(@PathVariable String skillId) {
        skillService.deleteSkill(skillId);
        return Result.success();
    }

    @PostMapping("/bindings")
    public Result<MerchantAgentBinding> createBinding(@RequestBody MerchantAgentBinding binding) {
        return Result.success(bindingService.createBinding(binding));
    }

    @GetMapping("/bindings")
    public Result<List<MerchantAgentBinding>> getAllBindings() {
        return Result.success(bindingService.getAllBindings());
    }

    @GetMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> getBindingByUserId(@PathVariable Long userId) {
        return bindingService.findByUserId(userId)
                .map(Result::success)
                .orElse(Result.error("绑定不存在"));
    }

    @PutMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> updateBinding(@PathVariable Long userId, @RequestBody MerchantAgentBinding binding) {
        return Result.success(bindingService.updateBinding(userId, binding));
    }

    @DeleteMapping("/bindings/{userId}")
    public Result<Void> deleteBinding(@PathVariable Long userId) {
        bindingService.deleteBinding(userId);
        return Result.success();
    }

    @PatchMapping("/bindings/{userId}")
    public Result<MerchantAgentBinding> toggleBinding(@PathVariable Long userId, @RequestParam boolean enabled) {
        return Result.success(bindingService.toggleBinding(userId, enabled));
    }
}