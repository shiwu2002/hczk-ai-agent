package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseService {
    List<KnowledgeBase> getAllKnowledgeBases();
    List<KnowledgeBase> getByOwnerType(String ownerType);
    List<KnowledgeBase> getByOwnerId(String ownerType, Long ownerId);
    KnowledgeBase getById(Long id);
    KnowledgeBase createKnowledgeBase(KnowledgeBase knowledgeBase);
    KnowledgeBase updateKnowledgeBase(Long id, KnowledgeBase knowledgeBase);
    void deleteKnowledgeBase(Long id);
    KnowledgeBase findByOwnerAndCollection(String ownerType, Long ownerId, String collectionName);
    KnowledgeBase findByAgentIdAndCollection(String agentId, String collectionName);
}
