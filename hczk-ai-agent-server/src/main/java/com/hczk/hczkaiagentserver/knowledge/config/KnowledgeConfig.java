package com.hczk.hczkaiagentserver.knowledge.config;

import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class KnowledgeConfig {

    private final MilvusManager milvusManager;

    @PostConstruct
    public void init() {
        log.info("Initializing Milvus Knowledge Service...");
        milvusManager.init();
        log.info("Milvus Knowledge Service initialized");
    }
}
