package com.hczk.hczkaiagentserver.config;

import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.entity.ToolDefinition;
import com.hczk.hczkaiagentserver.service.SkillService;
import com.hczk.hczkaiagentserver.service.ToolDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * CLI 工具市场 — 系统 Skill 初始化器
 *
 * 确保 "CLI工具市场" Skill 及其两个内置工具（cli_tools_list、cli_tools_install）在启动时存在。
 * 迁移 SQL 已包含 INSERT IGNORE，此初始化器作为补充保障（防止误删后无法恢复）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliMarketInitializer implements CommandLineRunner {

    private final SkillService skillService;
    private final ToolDefinitionService toolDefinitionService;

    private static final String CLI_MARKET_SKILL_NAME = "cli-market";

    @Override
    public void run(String... args) {
        ensureCliMarketSkill();
    }

    private void ensureCliMarketSkill() {
        Skill existing = skillService.getSkillByName(CLI_MARKET_SKILL_NAME);
        if (existing != null) {
            log.debug("CLI工具市场 Skill 已存在: skillId={}", existing.getId());
            return;
        }

        log.info("初始化 CLI工具市场 Skill...");

        // 1. 创建 Skill
        Skill skill = new Skill();
        skill.setName(CLI_MARKET_SKILL_NAME);
        skill.setDisplayName("CLI工具市场");
        skill.setCategory("utility");
        skill.setIcon("Terminal");
        skill.setVersion("1.0.0");
        skill.setDescription("CLI命令行工具市场，提供命令行工具的发现和按需安装能力。可浏览已启用的CLI工具列表，按需安装到本地环境。");
        skill.setStatus("active");
        skill.setVisibility("public");
        skill = skillService.createSkill(skill);
        log.info("CLI工具市场 Skill 已创建: skillId={}", skill.getId());

        // 2. 创建 cli_tools_list 工具
        ToolDefinition listTool = new ToolDefinition();
        listTool.setSkillId(skill.getId());
        listTool.setName("cli_tools_list");
        listTool.setDisplayName("列出可用CLI工具");
        listTool.setDescription("获取已启用的CLI命令行工具列表。返回工具名称、显示名称、描述、分类、版本、命令数量等信息。仅返回管理员在CLI市场启用的工具。");
        listTool.setType("builtin");
        listTool.setStatus("active");
        listTool.setInputSchema("{\"type\":\"object\",\"properties\":{},\"required\":[]}");
        toolDefinitionService.create(listTool);

        // 3. 创建 cli_tools_install 工具
        ToolDefinition installTool = new ToolDefinition();
        installTool.setSkillId(skill.getId());
        installTool.setName("cli_tools_install");
        installTool.setDisplayName("安装CLI工具");
        installTool.setDescription("安装指定的CLI命令行工具。安装后该工具的命令将注册为平台工具供智能体调用。参数cli_name为工具名称（从cli_tools_list获取）。");
        installTool.setType("builtin");
        installTool.setStatus("active");
        installTool.setInputSchema("{\"type\":\"object\",\"properties\":{\"cli_name\":{\"type\":\"string\",\"description\":\"要安装的CLI工具名称，从cli_tools_list接口获取\"}},\"required\":[\"cli_name\"]}");
        toolDefinitionService.create(installTool);

        log.info("CLI工具市场 Skill 初始化完成（含 cli_tools_list、cli_tools_install）");
    }
}
