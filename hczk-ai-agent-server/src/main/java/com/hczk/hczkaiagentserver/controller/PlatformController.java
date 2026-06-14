package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import com.hczk.hczkaiagentserver.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PlatformController {

    private final SkillService skillService;
    private final MerchantAgentBindingService bindingService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${agent.runtime.health-url:http://localhost:3000/api/health}")
    private String runtimeHealthUrl;

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

    /**
     * 代理检测通用智能体运行时健康状态
     * 对接文档: /api/health 返回 status(ok/degraded) + components + runtime 信息
     * 前端通过此接口间接检查运行时，避免跨域问题
     */
    @GetMapping("/runtime/health")
    public Result<Map<String, Object>> checkRuntimeHealth() {
        log.info("检测运行时健康状态, url={}", runtimeHealthUrl);
        try {
            Map<String, Object> body = restTemplate.getForObject(runtimeHealthUrl, Map.class);
            log.info("运行时响应: {}", body);
            if (body == null) {
                return buildHealthResponse(false, "运行时返回空响应", null);
            }

            Object statusObj = body.get("status");
            String status = statusObj != null ? statusObj.toString() : null;
            boolean online = "ok".equals(status) || "degraded".equals(status);
            String message = switch (status) {
                case "ok" -> "运行正常";
                case "degraded" -> "部分降级运行中";
                default -> "状态异常: " + status;
            };

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("online", online);
            result.put("status", status);
            result.put("message", message);
            result.put("url", runtimeHealthUrl);
            result.put("uptime", body.get("uptime"));
            result.put("version", body.get("version"));
            result.put("timestamp", body.get("timestamp"));
            result.put("components", body.get("components"));
            result.put("runtime", body.get("runtime"));

            return Result.success(result);
        } catch (Exception e) {
            log.error("通用智能体运行时检测异常: url={}, error={}", runtimeHealthUrl, e.getMessage(), e);
            return buildHealthResponse(false, "连接失败: " + e.getMessage(), null);
        }
    }

    private Result<Map<String, Object>> buildHealthResponse(boolean online, String message, Object rawBody) {
        return Result.success(Map.of(
                "online", online,
                "message", message,
                "url", runtimeHealthUrl,
                "rawBody", rawBody != null ? rawBody : ""
        ));
    }
}
