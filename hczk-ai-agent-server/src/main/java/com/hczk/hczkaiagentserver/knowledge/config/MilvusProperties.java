package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.milvus")
public class MilvusProperties {
    private String address = "localhost:19530";
    private String username = "";
    private String password = "";
    private long connectTimeoutMs = 10000;
    private long keepAliveTimeMs = 30000;
}
