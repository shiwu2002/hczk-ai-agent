package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ApiKey;

import java.util.List;

public interface ApiKeyService {
    List<ApiKey> getAllApiKeys();
    List<ApiKey> getApiKeysByUserId(Long userId);
    ApiKey createApiKey(Long userId, String name);
    void deleteApiKey(Long id);
    ApiKey getApiKeyByKey(String apiKey);
    ApiKey getActiveApiKey(String apiKey);
}
