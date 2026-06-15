package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ApiKey;

import java.math.BigDecimal;
import java.util.List;

public interface ApiKeyService {
    List<ApiKey> getAllApiKeys();
    List<ApiKey> getApiKeysByUserId(Long userId);
    ApiKey createApiKey(Long userId, String name, BigDecimal unitPrice, List<Long> modelIds);
    ApiKey updateApiKey(Long id, String name, BigDecimal unitPrice, List<Long> modelIds);
    void deleteApiKey(Long id);
    ApiKey getApiKeyByKey(String apiKey);
    ApiKey getActiveApiKey(String apiKey);
}
