package com.hczk.hczkaiagentserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * CLI-Anything 注册表项 DTO
 * 对应 registry.json 中 clis 数组的每一项
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CliRegistryItem {
    private String name;
    private String displayName;
    private String version;
    private String description;
    private String requires;
    private String homepage;
    private String sourceUrl;
    private String installCmd;
    private String entryPoint;
    /** SKILL.md 相对路径，如 skills/cli-anything-jumpserver/SKILL.md */
    private String skillMd;
    private String category;
    private List<Contributor> contributors;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contributor {
        private String name;
        private String url;
    }
}
