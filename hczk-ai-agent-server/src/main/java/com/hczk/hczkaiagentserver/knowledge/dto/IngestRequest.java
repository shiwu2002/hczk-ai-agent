package com.hczk.hczkaiagentserver.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IngestRequest {
    @NotBlank(message = "agent_id不能为空")
    @Size(max = 64, message = "agent_id最大64字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "agent_id仅允许字母、数字、下划线、连字符")
    private String agentId;

    @NotBlank(message = "text不能为空")
    private String text;

    @Size(max = 128, message = "collection名最大128字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "collection仅允许字母、数字、下划线、连字符")
    private String collection = "default";

    private String source = "api";
    private String title;
}
