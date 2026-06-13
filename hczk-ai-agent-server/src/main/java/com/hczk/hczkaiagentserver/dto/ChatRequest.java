package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long modelId;
    private String message;
    private Boolean stream = true;
}
