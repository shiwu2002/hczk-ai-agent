package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.mapper.ApiKeyMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存服务
 * 
 * 提供热点数据的缓存管理，包括：
 * - 用户余额缓存（5分钟过期）
 * - 用户信息缓存（24小时过期）
 * - API Key缓存（24小时过期）
 * 
 * 采用读写策略：读优先走缓存，写后失效缓存
 * Redis不可用时自动降级到数据库查询，不影响业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    /** Redis操作模板 */
    private final RedisTemplate<String, Object> redisTemplate;
    /** 用户数据访问 */
    private final UserMapper userMapper;
    /** API Key数据访问 */
    private final ApiKeyMapper apiKeyMapper;

    /** 用户余额缓存前缀 */
    private static final String USER_BALANCE_PREFIX = "user:balance:";
    /** 用户信息缓存前缀 */
    private static final String USER_PREFIX = "user:";
    /** API Key缓存前缀 */
    private static final String API_KEY_PREFIX = "apikey:";
    /** 通用缓存过期时间（小时） */
    private static final long CACHE_EXPIRE_HOURS = 24;
    /** 余额缓存过期时间（分钟）- 高频修改数据使用较短过期时间 */
    private static final long BALANCE_CACHE_EXPIRE_MINUTES = 5;

    /**
     * 获取用户余额（优先从缓存）
     * 
     * @param userId 用户ID
     * @return 用户余额，用户不存在返回BigDecimal.ZERO
     */
    public BigDecimal getUserBalance(Long userId) {
        String key = USER_BALANCE_PREFIX + userId;
        try {
            // 优先从Redis缓存读取
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return new BigDecimal(cached.toString());
            }
        } catch (Exception e) {
            // Redis故障降级到数据库查询
            log.warn("Redis 获取用户余额失败: userId={}, error={}", userId, e.getMessage());
        }

        // 缓存未命中或Redis不可用，查询数据库并回填缓存
        User user = userMapper.selectById(userId);
        if (user != null) {
            cacheUserBalance(userId, user.getBalance());
            return user.getBalance();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 缓存用户余额
     * 
     * @param userId  用户ID
     * @param balance 余额
     */
    public void cacheUserBalance(Long userId, BigDecimal balance) {
        try {
            String key = USER_BALANCE_PREFIX + userId;
            redisTemplate.opsForValue().set(key, balance.toString(), BALANCE_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 设置用户余额失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 失效用户余额缓存
     * 
     * @param userId 用户ID
     */
    public void invalidateUserBalance(Long userId) {
        try {
            String key = USER_BALANCE_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除用户余额缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取用户信息（优先从缓存）
     * 
     * @param userId 用户ID
     * @return 用户实体，不存在返回null
     */
    public User getUser(Long userId) {
        String key = USER_PREFIX + userId;
        try {
            // 优先从Redis缓存读取
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof User) {
                return (User) cached;
            }
        } catch (Exception e) {
            // Redis故障降级到数据库查询
            log.warn("Redis 获取用户失败: userId={}, error={}", userId, e.getMessage());
        }

        // 缓存未命中或Redis不可用，查询数据库并回填缓存
        User user = userMapper.selectById(userId);
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    /**
     * 缓存用户信息
     * 
     * @param user 用户实体
     */
    public void cacheUser(User user) {
        try {
            String key = USER_PREFIX + user.getId();
            redisTemplate.opsForValue().set(key, user, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 设置用户失败: userId={}, error={}", user.getId(), e.getMessage());
        }
    }

    /**
     * 失效用户缓存（包括余额）
     * 
     * @param userId 用户ID
     */
    public void invalidateUser(Long userId) {
        try {
            String key = USER_PREFIX + userId;
            redisTemplate.delete(key);
            invalidateUserBalance(userId);
        } catch (Exception e) {
            log.warn("Redis 删除用户缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 获取API Key（优先从缓存）
     * 
     * @param apiKey API Key字符串
     * @return API Key实体，不存在或未激活返回null
     */
    public ApiKey getApiKey(String apiKey) {
        String key = API_KEY_PREFIX + apiKey;
        try {
            // 优先从Redis缓存读取
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof ApiKey) {
                return (ApiKey) cached;
            }
        } catch (Exception e) {
            // Redis故障降级到数据库查询
            log.warn("Redis 获取 API Key 失败: key={}, error={}", apiKey.substring(0, Math.min(10, apiKey.length())) + "...", e.getMessage());
        }

        // 缓存未命中或Redis不可用，查询数据库并回填缓存（仅缓存status=0可用的Key）
        ApiKey apiKeyEntity = apiKeyMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getApiKey, apiKey));
        if (apiKeyEntity != null && apiKeyEntity.getStatus() == 0) {
            cacheApiKey(apiKeyEntity);
        }
        return apiKeyEntity;
    }

    /**
     * 缓存API Key
     * 
     * @param apiKey API Key实体
     */
    public void cacheApiKey(ApiKey apiKey) {
        try {
            String key = API_KEY_PREFIX + apiKey.getApiKey();
            redisTemplate.opsForValue().set(key, apiKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis 设置 API Key 失败: keyId={}, error={}", apiKey.getId(), e.getMessage());
        }
    }

    /**
     * 失效API Key缓存
     * 
     * @param apiKey API Key字符串
     */
    public void invalidateApiKey(String apiKey) {
        try {
            String key = API_KEY_PREFIX + apiKey;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除 API Key 缓存失败: key={}, error={}", apiKey.substring(0, Math.min(10, apiKey.length())) + "...", e.getMessage());
        }
    }

    /**
     * 根据ID失效API Key缓存
     * 
     * @param apiKeyId API Key ID
     */
    public void invalidateApiKeyById(Long apiKeyId) {
        try {
            ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
            if (apiKey != null) {
                invalidateApiKey(apiKey.getApiKey());
            }
        } catch (Exception e) {
            log.warn("Redis 删除 API Key 缓存失败: keyId={}, error={}", apiKeyId, e.getMessage());
        }
    }
}