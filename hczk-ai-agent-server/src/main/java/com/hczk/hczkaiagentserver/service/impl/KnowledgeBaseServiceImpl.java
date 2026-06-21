package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.KnowledgeBase;
import com.hczk.hczkaiagentserver.mapper.KnowledgeBaseMapper;
import com.hczk.hczkaiagentserver.service.KnowledgeBaseService;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RedisCacheService redisCache;
    private final ObjectMapper objectMapper;
    private static final String CACHE_PREFIX = "kb:bases:";

    @Override
    public List<KnowledgeBase> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectList(null);
    }

    @Override
    public List<KnowledgeBase> getByOwnerType(String ownerType) {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getOwnerType, ownerType));
    }

    @Override
    public List<KnowledgeBase> getByOwnerId(String ownerType, String ownerId) {
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getOwnerType, ownerType)
                        .eq(KnowledgeBase::getOwnerId, ownerId));
    }

    @Override
    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new RuntimeException("知识库不存在");
        }
        return kb;
    }

    @Override
    @Transactional
    public KnowledgeBase createKnowledgeBase(KnowledgeBase knowledgeBase) {
        if (knowledgeBase.getOwnerType() == null || knowledgeBase.getOwnerType().trim().isEmpty()) {
            throw new IllegalArgumentException("归属类型不能为空");
        }
        if (knowledgeBase.getOwnerId() == null) {
            throw new IllegalArgumentException("归属对象ID不能为空");
        }
        if (knowledgeBase.getAgentId() == null || knowledgeBase.getAgentId().trim().isEmpty()) {
            throw new IllegalArgumentException("agentId不能为空");
        }

        // 检查同一归属下是否已存在同名集合
        KnowledgeBase existing = findByOwnerAndCollection(
                knowledgeBase.getOwnerType(), knowledgeBase.getOwnerId(),
                knowledgeBase.getCollectionName());
        if (existing != null) {
            return existing;
        }

        knowledgeBaseMapper.insert(knowledgeBase);
        // 失效缓存：前缀失效 + 精确失效 findByAgentIdAndCollection 的独立缓存 key
        redisCache.invalidateByPrefix(CACHE_PREFIX);
        redisCache.deleteKey(CACHE_PREFIX + knowledgeBase.getAgentId() + ":" + knowledgeBase.getCollectionName());
        return knowledgeBase;
    }

    @Override
    @Transactional
    public KnowledgeBase updateKnowledgeBase(Long id, KnowledgeBase knowledgeBase) {
        KnowledgeBase existing = getById(id);
        existing.setName(knowledgeBase.getName());
        existing.setDescription(knowledgeBase.getDescription());
        existing.setRowCount(knowledgeBase.getRowCount());
        existing.setStatus(knowledgeBase.getStatus());
        knowledgeBaseMapper.updateById(existing);
        // 失效缓存：前缀失效 + 精确失效
        redisCache.invalidateByPrefix(CACHE_PREFIX);
        redisCache.deleteKey(CACHE_PREFIX + existing.getAgentId() + ":" + existing.getCollectionName());
        return existing;
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase existing = getById(id);
        knowledgeBaseMapper.deleteById(id);
        // 失效缓存：前缀失效 + 精确失效
        redisCache.invalidateByPrefix(CACHE_PREFIX);
        if (existing != null) {
            redisCache.deleteKey(CACHE_PREFIX + existing.getAgentId() + ":" + existing.getCollectionName());
        }
    }

    @Override
    public KnowledgeBase findByOwnerAndCollection(String ownerType, String ownerId, String collectionName) {
        return knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getOwnerType, ownerType)
                        .eq(KnowledgeBase::getOwnerId, ownerId)
                        .eq(KnowledgeBase::getCollectionName, collectionName));
    }

    @Override
    public KnowledgeBase findByAgentIdAndCollection(String agentId, String collectionName) {
        String cacheKey = CACHE_PREFIX + agentId + ":" + collectionName;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try { return objectMapper.readValue(cached, KnowledgeBase.class); }
            catch (Exception e) { log.debug("kb缓存反序列化失败，回源"); }
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getAgentId, agentId)
                        .eq(KnowledgeBase::getCollectionName, collectionName));
        if (kb != null) {
            try { redisCache.cacheJsonWithRandomTTL(cacheKey, objectMapper.writeValueAsString(kb)); } catch (Exception ignored) {}
        }
        return kb;
    }
}
