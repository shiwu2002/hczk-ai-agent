package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.relevance")
public class RelevanceProperties {
    private long halfLifeMs = 2592000000L; // 30 days
    private double minScore = 0.01;
    private double maxScore = 10.0;
    private double defaultScore = 1.0;
    private double adoptRatioWeight = 2.0;
    private double adoptBonusWeight = 0.1;
    private double updateThreshold = 0.01;
}
