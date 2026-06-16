package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import com.hczk.hczkaiagentserver.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台配置管理控制器
 * 管理各AI平台（DashScope、OpenAI等）的连接配置，支持测试连通性
 */
@RestController
@RequestMapping("/platforms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    /** 获取所有平台配置列表 */
    @GetMapping
    public Result<List<PlatformConfig>> getAllConfigs() {
        return Result.success(platformConfigService.getAllConfigs());
    }

    /** 根据平台类型获取配置 */
    @GetMapping("/{type}")
    public Result<PlatformConfig> getConfigByType(@PathVariable PlatformType type) {
        return Result.success(platformConfigService.getConfigByType(type));
    }

    /** 保存平台配置（新增或更新） */
    @PostMapping
    public Result<PlatformConfig> saveConfig(@RequestBody PlatformConfig config) {
        return Result.success(platformConfigService.saveConfig(config));
    }

    /** 测试指定平台的连接是否可用 */
    @PostMapping("/{type}/test")
    public Result<Boolean> testConnection(@PathVariable PlatformType type) {
        return Result.success(platformConfigService.testConnection(type));
    }
}
