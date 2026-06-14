package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.ingest")
public class IngestProperties {
    private int maxTextSize = 5242880; // 5MB
    private int chunkSize = 800;
    private int chunkOverlapSentences = 2;
    private int milvusBatchSize = 100;
}
