package com.hczk.hczkaiagentserver.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionInfo {
    private String name;
    private long rowCount;
    private String agentId;
    private String collectionName;
    private String ownerType;
    private Long ownerId;
    private String ownerName;
}
