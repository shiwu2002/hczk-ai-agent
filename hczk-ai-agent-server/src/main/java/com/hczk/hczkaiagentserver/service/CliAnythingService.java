package com.hczk.hczkaiagentserver.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hczk.hczkaiagentserver.config.CliAnythingProperties;
import com.hczk.hczkaiagentserver.dto.CliRegistry;
import com.hczk.hczkaiagentserver.dto.CliRegistryItem;
import com.hczk.hczkaiagentserver.entity.CliToolCommand;
import com.hczk.hczkaiagentserver.entity.CliToolRegistry;
import com.hczk.hczkaiagentserver.mapper.CliToolCommandMapper;
import com.hczk.hczkaiagentserver.mapper.CliToolRegistryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI-Anything 注册表服务（v3：平台只管元数据，智能体自行安装执行）
 *
 * 架构设计：
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ 平台（本服务）                                                    │
 * │  1. 定时从 GitHub 同步 registry.json + SKILL.md → 入库            │
 * │  2. 管理员"启用"工具 → 创建 Skill + ToolDefinition               │
 * │  3. 提供元数据 API → 智能体获取 install_cmd / entry_point / 命令  │
 * │  4. 不做 pip install，不做 ProcessBuilder 执行                    │
 * └──────────────────────────────────────────────────────────────────┘
 *         │ 元数据 API
 *         ▼
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ 智能体（远端服务）                                                │
 * │  1. 从平台发现已启用的 CLI 工具（endpoint: cli-anything://...）    │
 * │  2. 获取 install_cmd，在本地 pip install                         │
 * │  3. 通过 entry_point 在本地执行 CLI 命令                          │
 * │  4. 向平台汇报安装状态                                            │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CliAnythingService {

    private final CliAnythingProperties properties;
    private final CliToolRegistryMapper registryMapper;
    private final CliToolCommandMapper commandMapper;
    private final ObjectMapper objectMapper;

    private static final Pattern YAML_FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---", Pattern.DOTALL);
    private static final int MAX_RETRY_COUNT = 5;

    // ========== 查询接口（基于数据库，不依赖网络） ==========

    /** 获取所有 CLI 工具列表 */
    public List<CliToolRegistry> listClis() {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.orderByAsc("category").orderByAsc("name");
        List<CliToolRegistry> list = registryMapper.selectList(qw);
        for (CliToolRegistry item : list) {
            QueryWrapper<CliToolCommand> cq = new QueryWrapper<>();
            cq.eq("cli_name", item.getName());
            item.setCommandCount(Math.toIntExact(commandMapper.selectCount(cq)));
        }
        return list;
    }

    /** 按 name 获取单个 CLI 工具 */
    public CliToolRegistry getCliByName(String name) {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.eq("name", name);
        return registryMapper.selectOne(qw);
    }

    /** 获取指定 CLI 工具的命令列表 */
    public List<CliToolCommand> getCliCommands(String cliName) {
        QueryWrapper<CliToolCommand> qw = new QueryWrapper<>();
        qw.eq("cli_name", cliName).orderByAsc("command_group").orderByAsc("command_name");
        return commandMapper.selectList(qw);
    }

    /** 获取管理员已启用的 CLI 工具列表（基于 is_enabled 标记） */
    public List<CliToolRegistry> listEnabledClis() {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.eq("is_enabled", true).orderByAsc("name");
        return registryMapper.selectList(qw);
    }

    /** 获取同步失败的 CLI 工具列表 */
    public List<CliToolRegistry> listFailedSyncClis() {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.eq("sync_status", "failed").orderByAsc("name");
        return registryMapper.selectList(qw);
    }

    // ========== 定时同步（从远程拉取入库） ==========

    public int syncRegistryFromRemote() {
        log.info("开始同步 CLI-Anything 注册表...");
        CliRegistry registry = fetchRemoteRegistry();
        if (registry == null || registry.getClis() == null) {
            log.warn("无法获取远程注册表，跳过同步");
            return 0;
        }

        int synced = 0;
        for (CliRegistryItem item : registry.getClis()) {
            try {
                syncSingleCli(item);
                synced++;
            } catch (Exception e) {
                log.error("同步 CLI 工具失败: name={}, error={}", item.getName(), e.getMessage());
            }
        }
        log.info("CLI-Anything 注册表同步完成: 共 {} 个工具，成功同步 {} 个", registry.getClis().size(), synced);
        return synced;
    }

    private void syncSingleCli(CliRegistryItem item) {
        CliToolRegistry existing = getCliByName(item.getName());

        if (existing != null) {
            boolean needUpdate = !item.getVersion().equals(existing.getVersion())
                    || "failed".equals(existing.getSyncStatus());
            if (needUpdate) {
                existing.setDisplayName(item.getDisplayName());
                existing.setVersion(item.getVersion());
                existing.setDescription(item.getDescription());
                existing.setRequires(item.getRequires());
                existing.setHomepage(item.getHomepage());
                existing.setSourceUrl(item.getSourceUrl());
                existing.setInstallCmd(item.getInstallCmd());
                existing.setEntryPoint(item.getEntryPoint());
                existing.setSkillMd(item.getSkillMd());
                existing.setCategory(item.getCategory());
                try {
                    existing.setContributors(item.getContributors() != null
                            ? objectMapper.writeValueAsString(item.getContributors()) : null);
                } catch (Exception ignored) {}
                existing.setSyncStatus("synced");
                existing.setLastSyncAt(LocalDateTime.now());
                existing.setSyncError(null);
                existing.setSyncRetryCount(0);
                registryMapper.updateById(existing);
            }
        } else {
            CliToolRegistry reg = new CliToolRegistry();
            reg.setName(item.getName());
            reg.setDisplayName(item.getDisplayName());
            reg.setVersion(item.getVersion());
            reg.setDescription(item.getDescription());
            reg.setRequires(item.getRequires());
            reg.setHomepage(item.getHomepage());
            reg.setSourceUrl(item.getSourceUrl());
            reg.setInstallCmd(item.getInstallCmd());
            reg.setEntryPoint(item.getEntryPoint());
            reg.setSkillMd(item.getSkillMd());
            reg.setCategory(item.getCategory());
            try {
                reg.setContributors(item.getContributors() != null
                        ? objectMapper.writeValueAsString(item.getContributors()) : null);
            } catch (Exception ignored) {}
            reg.setSyncStatus("synced");
            reg.setLastSyncAt(LocalDateTime.now());
            reg.setSyncRetryCount(0);
            reg.setIsEnabled(false);          // 新工具默认未启用，需管理员手动启用
            reg.setInstallStatus("not_installed");
            registryMapper.insert(reg);
        }
    }

    public int syncSkillMdContent() {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.eq("sync_status", "synced")
                .isNotNull("skill_md")
                .ne("skill_md", "")
                .and(w -> w.isNull("skill_md_content").or().eq("skill_md_content", ""));
        List<CliToolRegistry> needSync = registryMapper.selectList(qw);
        if (needSync.isEmpty()) return 0;

        int synced = 0;
        for (CliToolRegistry item : needSync) {
            try {
                String content = fetchSkillMdContent(item.getSkillMd());
                if (content != null) {
                    item.setSkillMdContent(content);
                    item.setLastSyncAt(LocalDateTime.now());
                    registryMapper.updateById(item);
                    parseAndSaveCommands(item, content);
                    synced++;
                }
            } catch (Exception e) {
                log.warn("同步 SKILL.md 失败: cli={}, error={}", item.getName(), e.getMessage());
                markSyncFailed(item, e.getMessage());
            }
        }
        log.info("SKILL.md 同步完成: 需同步 {} 个，成功 {} 个", needSync.size(), synced);
        return synced;
    }

    private void markSyncFailed(CliToolRegistry item, String error) {
        int retryCount = item.getSyncRetryCount() != null ? item.getSyncRetryCount() : 0;
        retryCount++;
        item.setSyncRetryCount(retryCount);
        item.setSyncError(error);
        if (retryCount >= MAX_RETRY_COUNT) {
            item.setSyncStatus("failed");
            log.warn("CLI 工具同步达到最大重试次数，标记为 failed: name={}, retryCount={}", item.getName(), retryCount);
        }
        registryMapper.updateById(item);
    }

    // ========== 启用/禁用（管理员操作，仅翻转 is_enabled 标记） ==========

    /**
     * 启用 CLI 工具（管理员在市场启用，仅翻转标记，不创建 Skill）
     * 智能体需通过 cli_tools_list 发现工具，然后调用 cli_tools_install 安装
     */
    @Transactional
    public CliToolRegistry enableCli(String cliName) {
        CliToolRegistry cli = getCliByName(cliName);
        if (cli == null) throw new RuntimeException("CLI 工具不存在于注册表: " + cliName);
        if (!"synced".equals(cli.getSyncStatus()))
            throw new RuntimeException("CLI 工具尚未同步完成，无法启用: " + cliName);
        cli.setIsEnabled(true);
        registryMapper.updateById(cli);
        log.info("CLI 工具已启用（市场可见）: name={}", cliName);
        return cli;
    }

    /**
     * 禁用 CLI 工具（管理员在市场禁用，仅翻转标记，不删除已安装的 Skill）
     */
    @Transactional
    public void disableCli(String cliName) {
        CliToolRegistry cli = getCliByName(cliName);
        if (cli == null) throw new RuntimeException("CLI 工具不存在: " + cliName);
        cli.setIsEnabled(false);
        registryMapper.updateById(cli);
        log.info("CLI 工具已禁用（市场不可见）: name={}", cliName);
    }

    // ========== 智能体端 API（通过 cli_tools_list / cli_tools_install 调用） ==========

    /**
     * 获取可用 CLI 工具列表（供 cli_tools_list 调用）
     * 仅返回已启用 + 已同步的工具，智能体可据此选择安装
     */
    public List<Map<String, Object>> getAvailableClisForAgent() {
        QueryWrapper<CliToolRegistry> qw = new QueryWrapper<>();
        qw.eq("is_enabled", true).eq("sync_status", "synced").orderByAsc("name");
        List<CliToolRegistry> list = registryMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CliToolRegistry cli : list) {
            QueryWrapper<CliToolCommand> cq = new QueryWrapper<>();
            cq.eq("cli_name", cli.getName());
            long cmdCount = commandMapper.selectCount(cq);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", cli.getName());
            m.put("display_name", cli.getDisplayName());
            m.put("description", cli.getDescription());
            m.put("category", cli.getCategory());
            m.put("version", cli.getVersion());
            m.put("commands_count", cmdCount);
            m.put("installed", "installed".equals(cli.getInstallStatus()));
            result.add(m);
        }
        return result;
    }

    /**
     * 智能体获取 CLI 工具的完整元数据（供 cli_tools_install 调用）
     * 平台只返回元数据，不做 pip install，不创建 Skill 记录。
     * 智能体拿到 install_cmd、entry_point、commands 后自行在本地安装执行。
     */
    public Map<String, Object> installCliForAgent(String cliName) {
        CliToolRegistry cli = getCliByName(cliName);
        if (cli == null) throw new RuntimeException("CLI 工具不存在: " + cliName);
        if (!"synced".equals(cli.getSyncStatus()))
            throw new RuntimeException("CLI 工具尚未同步完成: " + cliName);
        if (!Boolean.TRUE.equals(cli.getIsEnabled()))
            throw new RuntimeException("CLI 工具未启用，需管理员先在市场启用: " + cliName);

        // 直接返回完整元数据（复用已有方法）
        return getCliMetadataForAgent(cliName);
    }

    // ========== 智能体端 API（提供元数据，智能体自行安装执行） ==========

    /** 获取指定 CLI 工具的完整元数据（供智能体安装和执行） */
    public Map<String, Object> getCliMetadataForAgent(String cliName) {
        CliToolRegistry cli = getCliByName(cliName);
        if (cli == null) return null;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", cli.getName());
        metadata.put("display_name", cli.getDisplayName());
        metadata.put("version", cli.getVersion());
        metadata.put("description", cli.getDescription());
        metadata.put("entry_point", cli.getEntryPoint());
        metadata.put("install_cmd", cli.getInstallCmd());
        metadata.put("requires", cli.getRequires());
        metadata.put("category", cli.getCategory());

        // 安装指引（含镜像，解决网络问题）
        Map<String, Object> installGuide = new LinkedHashMap<>();
        installGuide.put("primary", cli.getInstallCmd());
        String[] mirrors = parseMirrorUrls(properties.getMirrorRawBaseUrls());
        if (mirrors.length > 0 && cli.getSourceUrl() != null) {
            List<String> mirrorCmds = new ArrayList<>();
            for (String mirror : mirrors) {
                mirrorCmds.add("pip install git+" + mirror + "/" + cli.getName() + "/agent-harness");
            }
            installGuide.put("mirrors", mirrorCmds);
        }
        metadata.put("install_guide", installGuide);

        // 命令列表
        List<CliToolCommand> commands = getCliCommands(cliName);
        List<Map<String, Object>> commandList = new ArrayList<>();
        for (CliToolCommand cmd : commands) {
            Map<String, Object> cmdMap = new LinkedHashMap<>();
            cmdMap.put("group", cmd.getCommandGroup());
            cmdMap.put("name", cmd.getCommandName());
            cmdMap.put("description", cmd.getCommandDescription());
            cmdMap.put("full_command", cmd.getFullCommand());
            cmdMap.put("input_schema", cmd.getInputSchema());
            commandList.add(cmdMap);
        }
        metadata.put("commands", commandList);
        metadata.put("skill_md_content", cli.getSkillMdContent());

        return metadata;
    }

    /** 获取所有已启用 CLI 工具的元数据列表（供智能体批量发现） */
    public List<Map<String, Object>> listEnabledCliMetadata() {
        List<CliToolRegistry> enabled = listEnabledClis();
        List<Map<String, Object>> result = new ArrayList<>();
        for (CliToolRegistry cli : enabled) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("name", cli.getName());
            meta.put("display_name", cli.getDisplayName());
            meta.put("entry_point", cli.getEntryPoint());
            meta.put("install_cmd", cli.getInstallCmd());
            meta.put("description", cli.getDescription());
            result.add(meta);
        }
        return result;
    }

    /** 智能体汇报安装状态 */
    public void reportAgentInstallStatus(String cliName, String agentId, String status, String error) {
        log.info("智能体汇报安装状态: cliName={}, agentId={}, status={}", cliName, agentId, status);
    }

    // ========== 手动触发同步 ==========

    public Map<String, Object> manualSync() {
        int registryCount = syncRegistryFromRemote();
        int skillMdCount = syncSkillMdContent();
        return Map.of(
                "registry_synced", registryCount,
                "skill_md_synced", skillMdCount,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    public void resetFailedSync(String cliName) {
        CliToolRegistry cli = getCliByName(cliName);
        if (cli == null) throw new RuntimeException("CLI 工具不存在: " + cliName);
        cli.setSyncStatus("pending");
        cli.setSyncRetryCount(0);
        cli.setSyncError(null);
        registryMapper.updateById(cli);
    }

    // ========== 内部方法 ==========

    private CliRegistry fetchRemoteRegistry() {
        String json = fetchUrlWithRetry(properties.getRegistryUrl(), 2);
        if (json != null) {
            try { return objectMapper.readValue(json, CliRegistry.class); }
            catch (Exception e) { log.warn("主 URL 返回数据解析失败: {}", e.getMessage()); }
        }

        String[] mirrors = parseMirrorUrls(properties.getMirrorUrls());
        for (int i = 0; i < mirrors.length; i++) {
            log.info("尝试镜像源 #{}: {}", i + 1, mirrors[i]);
            json = fetchUrlWithRetry(mirrors[i], 2);
            if (json != null) {
                try { return objectMapper.readValue(json, CliRegistry.class); }
                catch (Exception e) { log.warn("镜像 #{} 返回数据解析失败: {}", i + 1, e.getMessage()); }
            }
        }

        log.error("所有源（主 URL + {} 个镜像）均无法获取注册表", mirrors.length);
        return null;
    }

    private String fetchSkillMdContent(String relativePath) {
        String url = properties.getRawBaseUrl() + "/" + relativePath;
        String content = fetchUrlWithRetry(url, 2);
        if (content != null) return content;

        String[] mirrors = parseMirrorUrls(properties.getMirrorRawBaseUrls());
        for (String mirror : mirrors) {
            String mirrorUrl = mirror + "/" + relativePath;
            log.info("尝试镜像获取 SKILL.md: {}", mirrorUrl);
            content = fetchUrlWithRetry(mirrorUrl, 2);
            if (content != null) return content;
        }

        log.error("所有源均无法获取 SKILL.md: {}", relativePath);
        return null;
    }

    private String[] parseMirrorUrls(String mirrorUrlsStr) {
        if (mirrorUrlsStr == null || mirrorUrlsStr.isBlank()) return new String[0];
        return Arrays.stream(mirrorUrlsStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    private String fetchUrlWithRetry(String url, int maxRetries) {
        Exception lastError = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                HttpClient client = createTolerantHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                        .header("User-Agent", "hczk-ai-agent/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return response.body();
                lastError = new RuntimeException("HTTP " + response.statusCode());
            } catch (IOException | InterruptedException e) { lastError = e; }
            if (i < maxRetries - 1) {
                try { Thread.sleep((long) Math.pow(2, i + 1) * 1000); }
                catch (InterruptedException ignored) {}
            }
        }
        log.warn("HTTP 请求重试 {} 次后仍失败: url={}, lastError={}", maxRetries, url,
                lastError != null ? lastError.getMessage() : "unknown");
        return null;
    }

    private HttpClient createTolerantHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            }, null);
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
    }

    private void parseAndSaveCommands(CliToolRegistry cli, String skillMdContent) {
        Map<String, Object> frontmatter = parseYamlFrontmatter(skillMdContent);
        if (frontmatter == null) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commands = (List<Map<String, Object>>) frontmatter.get("commands");
        if (commands == null || commands.isEmpty()) return;

        QueryWrapper<CliToolCommand> dq = new QueryWrapper<>();
        dq.eq("cli_name", cli.getName());
        commandMapper.delete(dq);

        String entryPoint = cli.getEntryPoint() != null ? cli.getEntryPoint() : "cli-anything-" + cli.getName();

        for (Map<String, Object> commandGroup : commands) {
            String group = (String) commandGroup.get("group");
            String groupDesc = (String) commandGroup.get("description");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subcommands = (List<Map<String, Object>>) commandGroup.get("subcommands");
            if (subcommands == null || subcommands.isEmpty()) continue;

            for (Map<String, Object> sub : subcommands) {
                if (sub.containsKey("subcommands")) continue;
                String subName = (String) sub.get("name");
                String subDesc = (String) sub.get("description");
                @SuppressWarnings("unchecked")
                List<String> options = (List<String>) sub.get("options");

                CliToolCommand cmd = new CliToolCommand();
                cmd.setCliName(cli.getName());
                cmd.setCommandGroup(group);
                cmd.setGroupDescription(groupDesc);
                cmd.setCommandName(subName);
                cmd.setCommandDescription(subDesc != null ? subDesc : groupDesc);
                cmd.setFullCommand(entryPoint + " " + group + " " + subName);
                try { cmd.setOptions(options != null ? objectMapper.writeValueAsString(options) : null); }
                catch (Exception ignored) {}
                cmd.setInputSchema(buildInputSchema(options, entryPoint + " " + group + " " + subName));
                commandMapper.insert(cmd);
            }
        }
    }

    private String buildInputSchema(List<String> options, String fullCommand) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        Map<String, Object> cmdProp = new LinkedHashMap<>();
        cmdProp.put("type", "string");
        cmdProp.put("description", "Full CLI command to execute (default: " + fullCommand + ")");
        properties.put("command", cmdProp);
        required.add("command");
        if (options != null) {
            for (String opt : options) {
                String cleanOpt = opt.replaceFirst("^--?", "").split("/")[0].split("=")[0].replace("-", "_");
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", "string");
                prop.put("description", opt);
                properties.put(cleanOpt, prop);
            }
        }
        schema.put("properties", properties);
        schema.put("required", required);
        try { return objectMapper.writeValueAsString(schema); }
        catch (Exception e) { return "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}"; }
    }

    private Map<String, Object> parseYamlFrontmatter(String content) {
        Matcher matcher = YAML_FRONTMATTER.matcher(content);
        if (!matcher.find()) return null;
        String yaml = matcher.group(1);
        try { return new org.yaml.snakeyaml.Yaml().load(yaml); }
        catch (Exception e) { log.warn("YAML frontmatter 解析失败: {}", e.getMessage()); return null; }
    }
}
