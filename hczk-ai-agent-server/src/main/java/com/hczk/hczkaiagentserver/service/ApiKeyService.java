package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ApiKey;

import java.math.BigDecimal;
import java.util.List;

public interface ApiKeyService {
    List<ApiKey> getAllApiKeys();
    List<ApiKey> getApiKeysByUserId(String userId);
    ApiKey createApiKey(String userId, String name, BigDecimal unitPrice, List<Long> modelIds);
    ApiKey updateApiKey(Long id, String name, BigDecimal unitPrice, List<Long> modelIds);
    void deleteApiKey(Long id);
    ApiKey getActiveApiKey(String apiKey);
}
