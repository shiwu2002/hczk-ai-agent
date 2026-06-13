package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import com.hczk.hczkaiagentserver.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/platforms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PlatformConfigController {

    private final PlatformConfigService platformConfigService;

    @GetMapping
    public Result<List<PlatformConfig>> getAllConfigs() {
        return Result.success(platformConfigService.getAllConfigs());
    }

    @GetMapping("/{type}")
    public Result<PlatformConfig> getConfigByType(@PathVariable PlatformType type) {
        return Result.success(platformConfigService.getConfigByType(type));
    }

    @PostMapping
    public Result<PlatformConfig> saveConfig(@RequestBody PlatformConfig config) {
        return Result.success(platformConfigService.saveConfig(config));
    }

    @PostMapping("/{type}/test")
    public Result<Boolean> testConnection(@PathVariable PlatformType type) {
        return Result.success(platformConfigService.testConnection(type));
    }
}
