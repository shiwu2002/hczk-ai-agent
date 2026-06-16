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
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存服务
 * 
 * 提供热点数据的缓存管理，包括：
 * - 用户余额缓存（5分钟过期）
 * - 用户信息缓存（24小时过期）
 * - API Key缓存（24小时过期）
 * - 工具定义缓存（20-30分钟随机过期，防缓存雪崩）
 * 
 * 采用读写策略：读优先走缓存，写后失效缓存
 * Redis不可用时自动降级到数据库查询，不影响业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final Random random = new Random();

    private static final String USER_BALANCE_PREFIX = "user:balance:";
    private static final String API_KEY_PREFIX = "apikey:";
    private static final String TOOLS_PREFIX = "tools:";
    private static final long CACHE_EXPIRE_HOURS = 24;
    private static final long BALANCE_CACHE_EXPIRE_MINUTES = 5;
    /** 工具定义缓存过期时间范围（分钟） */
    private static final int TOOLS_CACHE_MIN_MINUTES = 20;
    private static final int TOOLS_CACHE_MAX_MINUTES = 30;

    /**
     * 获取用户余额（优先从缓存）
     *
     * @param userId 用户ID（雪花ID）
     * @return 用户余额，用户不存在返回BigDecimal.ZERO
     */
    public BigDecimal getUserBalance(String userId) {
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
     * @param userId  用户ID（雪花ID）
     * @param balance 余额
     */
    public void cacheUserBalance(String userId, BigDecimal balance) {
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
     * @param userId 用户ID（雪花ID）
     */
    public void invalidateUserBalance(String userId) {
        try {
            String key = USER_BALANCE_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除用户余额缓存失败: userId={}, error={}", userId, e.getMessage());
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

    // ========== 工具定义缓存 ==========

    /** 通用：获取缓存的 JSON 字符串 */
    public String getCachedJson(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            return cached != null ? cached.toString() : null;
        } catch (Exception e) {
            log.warn("Redis 获取缓存失败: key={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    /** 通用：缓存 JSON 字符串（20-30分钟随机过期，防缓存雪崩） */
    public void cacheJsonWithRandomTTL(String cacheKey, String json) {
        try {
            int ttl = TOOLS_CACHE_MIN_MINUTES + random.nextInt(TOOLS_CACHE_MAX_MINUTES - TOOLS_CACHE_MIN_MINUTES + 1);
            redisTemplate.opsForValue().set(cacheKey, json, ttl, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 设置缓存失败: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    /** 通用：失效指定前缀的所有缓存 */
    public void invalidateByPrefix(String prefix) {
        try {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis 清除缓存失败: prefix={}, error={}", prefix, e.getMessage());
        }
    }

    /** 删除单个缓存 key */
    public void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 删除缓存key失败: key={}, error={}", key, e.getMessage());
        }
    }

    /** 构建工具缓存 key */
    public static String toolsKey(String suffix) { return TOOLS_PREFIX + suffix; }

    // ========== 待添加缓存前缀 ==========
    private static final String BINDINGS_PREFIX = "bindings:";
    private static final String SKILLS_ALL_KEY = "skills:all";
    private static final String AGENTS_ALL_KEY = "agents:all";
    private static final String KB_BASES_KEY = "kb:bases:all";
}