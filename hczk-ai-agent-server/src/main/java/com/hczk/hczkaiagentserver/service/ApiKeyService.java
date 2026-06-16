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

    /** 根据用户ID获取其API Key列表 */
    List<ApiKey> getApiKeysByUserId(Long userId);

    /** 创建API Key */
    ApiKey createApiKey(Long userId, String name, BigDecimal unitPrice, List<Long> modelIds);

    /** 更新API Key信息 */
    ApiKey updateApiKey(Long id, String name, BigDecimal unitPrice, List<Long> modelIds);

    /** 删除API Key */
    void deleteApiKey(Long id);

    /** 根据Key字符串查询（包含已禁用的） */
    ApiKey getApiKeyByKey(String apiKey);

    /** 根据Key字符串查询有效的API Key（仅返回启用状态的） */
    ApiKey getActiveApiKey(String apiKey);
}
