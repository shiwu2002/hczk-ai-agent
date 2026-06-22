package com.hczk.hczkaiagentserver.config;

import com.hczk.hczkaiagentserver.service.CliAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CLI-Anything 注册表定时同步任务
 *
 * 定时从远程 registry.json 拉取数据入库，确保前端展示不依赖 GitHub 实时可达
 * 同步策略：
 *   - registry.json 每 6 小时同步一次（元数据轻量，失败影响小）
 *   - SKILL.md 在 registry 同步后按需拉取（仅拉取未缓存的）
 *   - 失败自动重试，指数退避，最多 5 次后标记为 failed
 *
 * 可通过 cli-anything.sync-enabled=false 关闭定时同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cli-anything.sync-enabled", havingValue = "true", matchIfMissing = true)
public class CliRegistrySyncTask {

    private final CliAnythingService cliAnythingService;

    /**
     * 定时同步注册表元数据
     * 默认每 6 小时执行一次（可配置）
     */
    @Scheduled(fixedDelayString = "${cli-anything.sync-interval-ms:21600000}", initialDelayString = "${cli-anything.sync-initial-delay-ms:60000}")
    public void syncRegistry() {
        log.info("=== CLI-Anything 定时同步开始 ===");
        try {
            int registryCount = cliAnythingService.syncRegistryFromRemote();
            int skillMdCount = cliAnythingService.syncSkillMdContent();
            log.info("=== CLI-Anything 定时同步完成: registry={}, skill_md={} ===", registryCount, skillMdCount);
        } catch (Exception e) {
            log.error("CLI-Anything 定时同步异常: {}", e.getMessage(), e);
        }
    }
}
