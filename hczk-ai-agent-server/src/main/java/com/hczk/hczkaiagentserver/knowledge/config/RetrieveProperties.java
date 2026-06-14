package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.retrieve")
public class RetrieveProperties {
    private int defaultTopK = 5;
    private int maxTopK = 50;
    private double hybridConfidence = 0.7;
    private double vectorConfidence = 0.5;
    private double keywordConfidence = 0.3;
    private double fallbackConfidence = 0.2;
    private double questionWeight = 1.2;
    private int nprobe = 16;
}
