package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ApiKey;

import java.math.BigDecimal;
import java.util.List;

/**
 * API Key服务接口
 * 管理用户API密钥的创建、查询、更新和删除
 */
public interface ApiKeyService {

    /** 获取所有API Key */
    List<ApiKey> getAllApiKeys();
    List<ApiKey> getApiKeysByUserId(String userId);
    ApiKey createApiKey(String userId, String name, BigDecimal unitPrice, List<Long> modelIds);
    ApiKey updateApiKey(Long id, String name, BigDecimal unitPrice, List<Long> modelIds);

    /** 删除API Key */
    void deleteApiKey(Long id);
    ApiKey getActiveApiKey(String apiKey);
}
