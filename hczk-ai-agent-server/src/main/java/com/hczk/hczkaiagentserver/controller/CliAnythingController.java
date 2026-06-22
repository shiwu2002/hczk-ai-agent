package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.CliToolCommand;
import com.hczk.hczkaiagentserver.entity.CliToolRegistry;
import com.hczk.hczkaiagentserver.service.CliAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CLI-Anything 注册表管理控制器（v3：平台管元数据，智能体自行安装执行）
 *
 * 分两部分：
 * 1. /platform/cli-anything/** — 管理端（ADMIN），同步/启用/禁用
 * 2. /tools/cli-anything/**    — 智能体端，获取元数据/汇报状态
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CliAnythingController {

    private final CliAnythingService cliAnythingService;

    // ========== 管理端 API（/platform/cli-anything） ==========

    @GetMapping("/platform/cli-anything/clis")
    public Result<List<CliToolRegistry>> listClis(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "install_status", required = false) String installStatus) {
        List<CliToolRegistry> list = cliAnythingService.listClis();
        if (category != null && !category.isBlank()) {
            list = list.stream().filter(c -> category.equals(c.getCategory())).toList();
        }
        if (installStatus != null && !installStatus.isBlank()) {
            list = list.stream().filter(c -> installStatus.equals(c.getInstallStatus())).toList();
        }
        return Result.success(list);
    }

    @GetMapping("/platform/cli-anything/clis/{cliName}")
    public Result<CliToolRegistry> getCliDetail(@PathVariable String cliName) {
        CliToolRegistry cli = cliAnythingService.getCliByName(cliName);
        if (cli == null) return Result.error("CLI 工具不存在: " + cliName);
        return Result.success(cli);
    }

    @GetMapping("/platform/cli-anything/clis/{cliName}/commands")
    public Result<List<CliToolCommand>> getCliCommands(@PathVariable String cliName) {
        return Result.success(cliAnythingService.getCliCommands(cliName));
    }

    @GetMapping("/platform/cli-anything/enabled")
    public Result<List<CliToolRegistry>> listEnabled() {
        return Result.success(cliAnythingService.listEnabledClis());
    }

    @GetMapping("/platform/cli-anything/failed")
    public Result<List<CliToolRegistry>> listFailed() {
        return Result.success(cliAnythingService.listFailedSyncClis());
    }

    /** 启用 CLI 工具（管理员在市场启用，仅翻转 is_enabled 标记，不创建 Skill）
     *  智能体需通过 cli_tools_list 发现工具，然后调用 cli_tools_install 安装 */
    @PostMapping("/platform/cli-anything/clis/{cliName}/enable")
    public Result<CliToolRegistry> enableCli(@PathVariable String cliName) {
        try {
            return Result.success(cliAnythingService.enableCli(cliName));
        } catch (Exception e) {
            log.error("启用 CLI 工具失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /** 禁用 CLI 工具（管理员在市场禁用，仅翻转 is_enabled 标记，不删除已安装的 Skill） */
    @PostMapping("/platform/cli-anything/clis/{cliName}/disable")
    public Result<Void> disableCli(@PathVariable String cliName) {
        try {
            cliAnythingService.disableCli(cliName);
            return Result.success();
        } catch (Exception e) {
            log.error("禁用 CLI 工具失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/platform/cli-anything/sync")
    public Result<Map<String, Object>> manualSync() {
        try {
            return Result.success(cliAnythingService.manualSync());
        } catch (Exception e) {
            log.error("手动同步失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/platform/cli-anything/clis/{cliName}/reset-sync")
    public Result<Void> resetFailedSync(@PathVariable String cliName) {
        try {
            cliAnythingService.resetFailedSync(cliName);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ========== 智能体端 API（/tools/cli-anything） ==========

    /** 获取所有已启用 CLI 工具的元数据列表 */
    @GetMapping("/tools/cli-anything/clis")
    public Result<List<Map<String, Object>>> listAgentClis() {
        return Result.success(cliAnythingService.listEnabledCliMetadata());
    }

    /** 获取指定 CLI 工具的完整元数据（install_cmd, entry_point, commands, skill_md 等） */
    @GetMapping("/tools/cli-anything/clis/{cliName}")
    public Result<Map<String, Object>> getCliMetadata(@PathVariable String cliName) {
        Map<String, Object> metadata = cliAnythingService.getCliMetadataForAgent(cliName);
        if (metadata == null) return Result.error("CLI 工具不存在或未启用: " + cliName);
        return Result.success(metadata);
    }

    /** 智能体汇报安装状态 */
    @PostMapping("/tools/cli-anything/clis/{cliName}/report")
    public Result<Void> reportInstallStatus(
            @PathVariable String cliName,
            @RequestBody Map<String, String> body) {
        cliAnythingService.reportAgentInstallStatus(
                cliName,
                body.get("agent_id"),
                body.get("status"),
                body.get("error"));
        return Result.success();
    }
}
