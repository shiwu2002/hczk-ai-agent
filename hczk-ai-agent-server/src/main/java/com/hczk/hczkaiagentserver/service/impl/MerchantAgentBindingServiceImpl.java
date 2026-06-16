package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.mapper.MerchantAgentBindingMapper;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAgentBindingServiceImpl implements MerchantAgentBindingService {

    private final MerchantAgentBindingMapper bindingMapper;
    private final RedisCacheService redisCache;
    private final ObjectMapper objectMapper;
    private static final String CACHE_PREFIX = "bindings:user:";

    @Override
    @Transactional
    public MerchantAgentBinding createBinding(MerchantAgentBinding binding) {
        if (binding.getUserId() == null) {
            throw new RuntimeException("必须提供 user_id");
        }
        Optional<MerchantAgentBinding> existing = findByUserId(binding.getUserId());
        if (existing.isPresent()) {
            throw new RuntimeException("用户已存在绑定: userId=" + binding.getUserId());
        }

        // 至少需要一种绑定方式：agentId 或 agentEndpoint
        if (binding.getAgentId() == null && binding.getAgentEndpoint() == null) {
            throw new RuntimeException("必须提供 agent_id 或 agent_endpoint");
        }

        bindingMapper.insert(binding);
        redisCache.deleteKey(CACHE_PREFIX + binding.getUserId());
        log.info("创建绑定: userId={}, agentId={}, endpoint={}",
                binding.getUserId(), binding.getAgentId(),
                binding.getAgentEndpoint());
        return binding;
    }

    @Override
    @Transactional
    public MerchantAgentBinding updateBinding(Long userId, MerchantAgentBinding binding) {
        Optional<MerchantAgentBinding> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            throw new RuntimeException("绑定不存在: userId=" + userId);
        }

        MerchantAgentBinding update = existing.get();
        if (binding.getAgentId() != null) {
            update.setAgentId(binding.getAgentId());
        }
        if (binding.getAgentEndpoint() != null) {
            update.setAgentEndpoint(binding.getAgentEndpoint());
            update.setAgentAuthHeader(binding.getAgentAuthHeader());
        }
        if (binding.getPersonaOverride() != null) {
            update.setPersonaOverride(binding.getPersonaOverride());
        }
        if (binding.getCapabilitiesOverride() != null) {
            update.setCapabilitiesOverride(binding.getCapabilitiesOverride());
        }
        if (binding.getConfigOverride() != null) {
            update.setConfigOverride(binding.getConfigOverride());
        }
        if (binding.getToolsConfig() != null) {
            update.setToolsConfig(binding.getToolsConfig());
        }

        bindingMapper.updateById(update);
        redisCache.deleteKey(CACHE_PREFIX + userId);
        log.info("更新绑定: userId={}", userId);
        return update;
    }

    @Override
    @Transactional
    public void deleteBinding(Long userId) {
        Optional<MerchantAgentBinding> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            throw new RuntimeException("绑定不存在: userId=" + userId);
        }
        bindingMapper.deleteById(existing.get().getId());
        redisCache.deleteKey(CACHE_PREFIX + userId);
        log.info("删除绑定: userId={}", userId);
    }

    @Override
    public Optional<MerchantAgentBinding> findByUserId(Long userId) {
        String cacheKey = CACHE_PREFIX + userId;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                MerchantAgentBinding binding = objectMapper.readValue(cached, MerchantAgentBinding.class);
                return Optional.ofNullable(binding);
            } catch (Exception e) { log.debug("绑定缓存反序列化失败，回源: userId={}", userId); }
        }
        LambdaQueryWrapper<MerchantAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantAgentBinding::getUserId, userId);
        MerchantAgentBinding binding = bindingMapper.selectOne(wrapper);
        if (binding != null) {
            try { redisCache.cacheJsonWithRandomTTL(cacheKey, objectMapper.writeValueAsString(binding)); } catch (Exception ignored) {}
        }
        return Optional.ofNullable(binding);
    }

    @Override
    public List<MerchantAgentBinding> getAllBindings() {
        return bindingMapper.selectList(null);
    }

    @Override
    @Transactional
    public MerchantAgentBinding toggleBinding(Long userId, boolean enabled) {
        Optional<MerchantAgentBinding> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            throw new RuntimeException("绑定不存在: userId=" + userId);
        }
        MerchantAgentBinding binding = existing.get();
        binding.setEnabled(enabled);
        bindingMapper.updateById(binding);
        redisCache.deleteKey(CACHE_PREFIX + userId);
        log.info("切换绑定状态: userId={}, enabled={}", userId, enabled);
        return binding;
    }
}
