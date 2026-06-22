package com.hczk.hczkaiagentserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * CLI-Anything 注册表整体 DTO
 * 对应 registry.json 的顶层结构
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CliRegistry {
    private Meta meta;
    private List<CliRegistryItem> clis;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String repo;
        private String description;
        private String updated;
    }
}
