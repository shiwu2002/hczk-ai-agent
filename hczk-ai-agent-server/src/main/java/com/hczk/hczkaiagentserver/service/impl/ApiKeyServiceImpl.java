package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.mapper.ApiKeyMapper;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import com.hczk.hczkaiagentserver.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;

    @Override
    public List<ApiKey> getAllApiKeys() {
        return apiKeyMapper.selectList(null);
    }

    @Override
    public List<ApiKey> getApiKeysByUserId(Long userId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId));
    }

    @Override
    @Transactional
    public ApiKey createApiKey(Long userId, String name, BigDecimal unitPrice, List<Long> modelIds) {
        ApiKey key = new ApiKey();
        key.setName(name);
        key.setApiKey(ApiKeyGenerator.generateKey());
        key.setUserId(userId);
        key.setUnitPrice(unitPrice != null ? unitPrice : BigDecimal.ZERO);
        key.setModelIds(modelIds);
        apiKeyMapper.insert(key);
        return key;
    }

    @Override
    @Transactional
    public ApiKey updateApiKey(Long id, String name, BigDecimal unitPrice, List<Long> modelIds) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new RuntimeException("API Key 不存在");
        }
        if (name != null) {
            key.setName(name);
        }
        if (unitPrice != null) {
            key.setUnitPrice(unitPrice);
        }
        if (modelIds != null) {
            key.setModelIds(modelIds);
        }
        apiKeyMapper.updateById(key);
        return key;
    }

    @Override
    @Transactional
    public void deleteApiKey(Long id) {
        apiKeyMapper.deleteById(id);
    }

    @Override
    public ApiKey getApiKeyByKey(String apiKey) {
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getApiKey, apiKey));
        if (key == null) {
            throw new RuntimeException("API Key 不存在");
        }
        return key;
    }

    @Override
    public ApiKey getActiveApiKey(String apiKey) {
        ApiKey key = apiKeyMapper.findByApiKey(apiKey);
        if (key == null) {
            throw new RuntimeException("API Key 无效或已禁用");
        }
        return key;
    }
}
