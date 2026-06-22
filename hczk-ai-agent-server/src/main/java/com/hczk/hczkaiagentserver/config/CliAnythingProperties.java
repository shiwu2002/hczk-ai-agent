package com.hczk.hczkaiagentserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CLI-Anything 注册表配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "cli-anything")
public class CliAnythingProperties {
    /** 注册表 JSON URL */
    private String registryUrl = "https://hkuds.github.io/CLI-Anything/registry.json";
    /** GitHub Raw 基础 URL，用于拼接 SKILL.md 等资源路径 */
    private String rawBaseUrl = "https://raw.githubusercontent.com/HKUDS/CLI-Anything/main";
    /** 备用镜像 URL 列表（逗号分隔），主 URL 失败时依次尝试 */
    private String mirrorUrls = "https://ghp.ci/https://raw.githubusercontent.com/HKUDS/CLI-Anything/main/registry.json,https://mirror.ghproxy.com/https://raw.githubusercontent.com/HKUDS/CLI-Anything/main/registry.json";
    /** 备用镜像 Raw 基础 URL（逗号分隔），主 URL 失败时依次尝试 */
    private String mirrorRawBaseUrls = "https://ghp.ci/https://raw.githubusercontent.com/HKUDS/CLI-Anything/main,https://mirror.ghproxy.com/https://raw.githubusercontent.com/HKUDS/CLI-Anything/main";
    /** 定时同步开关 */
    private boolean syncEnabled = true;
    /** 定时同步间隔（毫秒），默认 6 小时 */
    private long syncIntervalMs = 21600000L;
    /** 启动后首次同步延迟（毫秒），默认 1 分钟 */
    private long syncInitialDelayMs = 60000L;
    /** HTTP 连接超时（毫秒） */
    private int connectTimeoutMs = 15000;
    /** HTTP 读取超时（毫秒） */
    private int readTimeoutMs = 60000;
}
