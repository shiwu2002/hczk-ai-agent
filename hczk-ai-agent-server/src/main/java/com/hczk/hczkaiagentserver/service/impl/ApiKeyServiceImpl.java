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

/**
 * API Key服务实现类
 *
 * 提供API Key的增删改查功能。
 * API Key是计费的核心载体，绑定模型列表(modelIds)和统一Token单价(unitPrice)。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    /** API Key数据访问 */
    private final ApiKeyMapper apiKeyMapper;

    /**
     * 获取所有API Key列表
     *
     * @return API Key列表
     */
    @Override
    public List<ApiKey> getAllApiKeys() {
        return apiKeyMapper.selectList(null);
    }

    /**
     * 根据用户ID获取API Key列表
     *
     * @param userId 用户ID
     * @return 该用户的API Key列表
     */
    @Override
    public List<ApiKey> getApiKeysByUserId(Long userId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId));
    }

    /**
     * 创建API Key
     *
     * 自动生成sk-hczk-前缀的密钥串，设置统一Token单价和绑定模型列表。
     * 新建Key默认可用（status=0）。
     *
     * @param userId    归属用户ID
     * @param name      密钥备注名称
     * @param unitPrice 统一Token单价（元/千Tokens），null时默认0（免费）
     * @param modelIds  绑定的模型ID列表
     * @return 创建后的API Key（含自增id和生成的密钥串）
     */
    @Override
    @Transactional
    public ApiKey createApiKey(Long userId, String name, BigDecimal unitPrice, List<Long> modelIds) {
        // 校验：每个用户只能拥有一个API Key
        Long existingCount = apiKeyMapper.selectCount(
                new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getUserId, userId));
        if (existingCount > 0) {
            throw new RuntimeException("该用户已存在API Key，每个账户仅允许一个API Key");
        }

        ApiKey key = new ApiKey();
        key.setName(name);
        key.setApiKey(ApiKeyGenerator.generateKey());
        key.setUserId(userId);
        key.setUnitPrice(unitPrice != null ? unitPrice : BigDecimal.ZERO);
        key.setModelIds(modelIds);
        // 确保新建API Key默认可用
        if (key.getStatus() == null) {
            key.setStatus(0);
        }
        apiKeyMapper.insert(key);
        return key;
    }

    /**
     * 更新API Key配置
     *
     * 仅更新非null字段（名称、单价、绑定模型），支持部分更新
     *
     * @param id        API Key ID
     * @param name      新名称（null则不更新）
     * @param unitPrice 新单价（null则不更新）
     * @param modelIds  新绑定模型列表（null则不更新）
     * @return 更新后的API Key
     * @throws RuntimeException API Key不存在
     */
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

    /**
     * 删除API Key
     *
     * @param id API Key ID
     */
    @Override
    @Transactional
    public void deleteApiKey(Long id) {
        apiKeyMapper.deleteById(id);
    }

    /**
     * 根据密钥串查询API Key（不校验状态）
     *
     * @param apiKey 密钥串
     * @return API Key实体
     * @throws RuntimeException API Key不存在
     */
    @Override
    public ApiKey getApiKeyByKey(String apiKey) {
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>().eq(ApiKey::getApiKey, apiKey));
        if (key == null) {
            throw new RuntimeException("API Key 不存在");
        }
        return key;
    }

    /**
     * 根据密钥串查询可用的API Key（仅返回status=0的Key）
     *
     * @param apiKey 密钥串
     * @return API Key实体
     * @throws RuntimeException API Key无效或已禁用
     */
    @Override
    public ApiKey getActiveApiKey(String apiKey) {
        ApiKey key = apiKeyMapper.findByApiKey(apiKey);
        if (key == null) {
            throw new RuntimeException("API Key 无效或已禁用");
        }
        return key;
    }
}
