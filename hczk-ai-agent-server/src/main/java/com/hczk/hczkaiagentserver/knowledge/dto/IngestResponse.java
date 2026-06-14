package com.hczk.hczkaiagentserver.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private int totalChunks;
    private int ingestedChunks;
    private int skippedChunks;
}
