package com.hczk.hczkaiagentserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 为 @Async 提供有界线程池，避免默认 SimpleAsyncTaskExecutor 无限创建线程导致 OOM
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 知识库相关异步任务线程池（如 asyncRecordHit）
     * 有界队列 + 拒绝策略：队列满时丢弃任务（命中统计可容忍丢失）
     */
    @Bean("knowledgeTaskExecutor")
    public Executor knowledgeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("kb-async-");
        // 队列满时丢弃任务，避免阻塞主流程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        log.info("知识库异步线程池已初始化: core=4, max=16, queue=500");
        return executor;
    }
}
