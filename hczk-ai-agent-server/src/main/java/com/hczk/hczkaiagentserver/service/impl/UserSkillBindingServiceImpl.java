package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.Skill;
import com.hczk.hczkaiagentserver.entity.UserSkillBinding;
import com.hczk.hczkaiagentserver.mapper.SkillMapper;
import com.hczk.hczkaiagentserver.mapper.UserSkillBindingMapper;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.hczk.hczkaiagentserver.service.UserSkillBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户与私有工具组绑定服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSkillBindingServiceImpl implements UserSkillBindingService {

    private final UserSkillBindingMapper bindingMapper;
    private final SkillMapper skillMapper;
    private final RedisCacheService redisCache;

    /** 缓存 key 前缀：用户绑定的 skillId 集合 */
    private static final String CACHE_USER_SKILLS = "usb:user:";
    /** 缓存 key 前缀：skill 绑定的 userId 集合 */
    private static final String CACHE_SKILL_USERS = "usb:skill:";
    /** 缓存 key 前缀：用户对 skill 的访问权限 */
    private static final String CACHE_ACCESS = "usb:access:";

    @Override
    @Transactional
    public UserSkillBinding bind(String userId, String skillId) {
        if (userId == null || userId.isBlank()) throw new RuntimeException("userId 不能为空");
        if (skillId == null || skillId.isBlank()) throw new RuntimeException("skillId 不能为空");

        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) throw new RuntimeException("工具组不存在: " + skillId);

        // 查找已有绑定（含已禁用的）
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId).eq(UserSkillBinding::getSkillId, skillId);
        UserSkillBinding existing = bindingMapper.selectOne(qw);

        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getEnabled())) {
                existing.setEnabled(true);
                bindingMapper.updateById(existing);
            }
            invalidateCache(userId, skillId);
            log.info("用户绑定工具组（已存在，启用）: userId={}, skillId={}", userId, skillId);
            return existing;
        }

        UserSkillBinding binding = new UserSkillBinding();
        binding.setUserId(userId);
        binding.setSkillId(skillId);
        binding.setEnabled(true);
        bindingMapper.insert(binding);
        invalidateCache(userId, skillId);
        log.info("用户绑定工具组: userId={}, skillId={}", userId, skillId);
        return binding;
    }

    @Override
    @Transactional
    public void unbind(String userId, String skillId) {
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId).eq(UserSkillBinding::getSkillId, skillId);
        int deleted = bindingMapper.delete(qw);
        invalidateCache(userId, skillId);
        log.info("解除用户工具组绑定: userId={}, skillId={}, deleted={}", userId, skillId, deleted);
    }

    @Override
    @Transactional
    public UserSkillBinding toggle(String userId, String skillId, boolean enabled) {
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId).eq(UserSkillBinding::getSkillId, skillId);
        UserSkillBinding binding = bindingMapper.selectOne(qw);
        if (binding == null) throw new RuntimeException("绑定不存在: userId=" + userId + ", skillId=" + skillId);
        binding.setEnabled(enabled);
        bindingMapper.updateById(binding);
        invalidateCache(userId, skillId);
        log.info("切换用户工具组绑定状态: userId={}, skillId={}, enabled={}", userId, skillId, enabled);
        return binding;
    }

    @Override
    public List<String> getBoundSkillIds(String userId) {
        String cacheKey = CACHE_USER_SKILLS + userId;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                return objectMapper(cached);
            } catch (Exception e) {
                log.debug("用户绑定 skillIds 缓存反序列化失败，回源: userId={}", userId);
            }
        }
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId).eq(UserSkillBinding::getEnabled, true);
        List<UserSkillBinding> list = bindingMapper.selectList(qw);
        List<String> skillIds = list.stream().map(UserSkillBinding::getSkillId).collect(Collectors.toList());
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(skillIds));
        } catch (Exception ignored) {}
        return skillIds;
    }

    @Override
    public List<UserSkillBinding> getBindingsByUserId(String userId) {
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId).orderByDesc(UserSkillBinding::getCreatedAt);
        return bindingMapper.selectList(qw);
    }

    @Override
    public List<String> getBoundUserIds(String skillId) {
        String cacheKey = CACHE_SKILL_USERS + skillId;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                return objectMapper(cached);
            } catch (Exception e) {
                log.debug("skill 绑定 userIds 缓存反序列化失败，回源: skillId={}", skillId);
            }
        }
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getSkillId, skillId).eq(UserSkillBinding::getEnabled, true);
        List<UserSkillBinding> list = bindingMapper.selectList(qw);
        List<String> userIds = list.stream().map(UserSkillBinding::getUserId).collect(Collectors.toList());
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(userIds));
        } catch (Exception ignored) {}
        return userIds;
    }

    @Override
    public boolean canAccess(String userId, String skillId) {
        if (skillId == null) return false;
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) return false;
        // public 直接可访问
        if ("public".equals(skill.getVisibility())) return true;
        // private 需要绑定且启用
        if (userId == null || userId.isBlank()) return false;

        String cacheKey = CACHE_ACCESS + userId + ":" + skillId;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        LambdaQueryWrapper<UserSkillBinding> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSkillBinding::getUserId, userId)
          .eq(UserSkillBinding::getSkillId, skillId)
          .eq(UserSkillBinding::getEnabled, true);
        boolean ok = bindingMapper.selectCount(qw) > 0;
        try {
            redisCache.cacheJsonWithRandomTTL(cacheKey, ok ? "1" : "0");
        } catch (Exception ignored) {}
        return ok;
    }

    private void invalidateCache(String userId, String skillId) {
        if (userId != null) {
            redisCache.deleteKey(CACHE_USER_SKILLS + userId);
            // 失效该用户对所有 skill 的访问权限缓存（前缀匹配 usb:access:{userId}:）
            redisCache.invalidateByPrefix(CACHE_ACCESS + userId + ":");
        }
        if (skillId != null) {
            redisCache.deleteKey(CACHE_SKILL_USERS + skillId);
            // 失效所有用户对该 skill 的访问权限缓存（访问缓存 TTL 较短，这里清前缀无法精确匹配，
            // 直接清整个 access 前缀，量级可控）
            redisCache.invalidateByPrefix(CACHE_ACCESS);
        }
        // 精确失效单条访问缓存
        if (userId != null && skillId != null) {
            redisCache.deleteKey(CACHE_ACCESS + userId + ":" + skillId);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> objectMapper(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
    }
}
