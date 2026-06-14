package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.embedding")
public class EmbeddingProperties {
    private String baseUrl = "http://127.0.0.1:1234/v1";
    private String apiKey = "";
    private String model = "text-embedding-qwen3-embedding";
    private int dimension = 2560;
    private long timeoutMs = 30000;
    private int batchSize = 20;
    private int cacheMaxSize = 10000;
}
