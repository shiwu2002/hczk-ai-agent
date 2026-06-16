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
    private static final String CACHE_PREFIX = "bindings:merchant:";

    @Override
    @Transactional
    public MerchantAgentBinding createBinding(MerchantAgentBinding binding) {
        Optional<MerchantAgentBinding> existing = findByMerchantId(binding.getMerchantId());
        if (existing.isPresent()) {
            throw new RuntimeException("商家已存在绑定: " + binding.getMerchantId());
        }

        // 至少需要一种绑定方式：agentId、skillId 或 agentEndpoint
        if (binding.getAgentId() == null && binding.getSkillId() == null && binding.getAgentEndpoint() == null) {
            throw new RuntimeException("必须提供 agent_id、skill_id 或 agent_endpoint");
        }

        // skillId 和 agentEndpoint 不能同时提供
        if (binding.getSkillId() != null && binding.getAgentEndpoint() != null) {
            throw new RuntimeException("skill_id 和 agent_endpoint 不能同时提供");
        }

        bindingMapper.insert(binding);
        redisCache.deleteKey(CACHE_PREFIX + binding.getMerchantId());
        log.info("创建商家绑定: merchantId={}, agentId={}, skillId={}, endpoint={}, apiKeyId={}",
                binding.getMerchantId(), binding.getAgentId(), binding.getSkillId(),
                binding.getAgentEndpoint(), binding.getApiKeyId());
        return binding;
    }

    @Override
    @Transactional
    public MerchantAgentBinding updateBinding(String merchantId, MerchantAgentBinding binding) {
        Optional<MerchantAgentBinding> existing = findByMerchantId(merchantId);
        if (existing.isEmpty()) {
            throw new RuntimeException("商家绑定不存在: " + merchantId);
        }

        MerchantAgentBinding update = existing.get();
        if (binding.getSkillId() != null) {
            update.setSkillId(binding.getSkillId());
            update.setAgentEndpoint(null);
            update.setAgentAuthHeader(null);
        }
        if (binding.getAgentEndpoint() != null) {
            update.setAgentEndpoint(binding.getAgentEndpoint());
            update.setAgentAuthHeader(binding.getAgentAuthHeader());
            update.setSkillId(null);
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
        redisCache.deleteKey(CACHE_PREFIX + merchantId);
        log.info("更新商家绑定: merchantId={}", merchantId);
        return update;
    }

    @Override
    @Transactional
    public void deleteBinding(String merchantId) {
        Optional<MerchantAgentBinding> existing = findByMerchantId(merchantId);
        if (existing.isEmpty()) {
            throw new RuntimeException("商家绑定不存在: " + merchantId);
        }
        bindingMapper.deleteById(existing.get().getId());
        redisCache.deleteKey(CACHE_PREFIX + merchantId);
        log.info("删除商家绑定: merchantId={}", merchantId);
    }

    @Override
    public Optional<MerchantAgentBinding> findByMerchantId(String merchantId) {
        String cacheKey = CACHE_PREFIX + merchantId;
        String cached = redisCache.getCachedJson(cacheKey);
        if (cached != null) {
            try {
                MerchantAgentBinding binding = objectMapper.readValue(cached, MerchantAgentBinding.class);
                return Optional.ofNullable(binding);
            } catch (Exception e) { log.debug("绑定缓存反序列化失败，回源: merchantId={}", merchantId); }
        }
        LambdaQueryWrapper<MerchantAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantAgentBinding::getMerchantId, merchantId);
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
    public MerchantAgentBinding toggleBinding(String merchantId, boolean enabled) {
        Optional<MerchantAgentBinding> existing = findByMerchantId(merchantId);
        if (existing.isEmpty()) {
            throw new RuntimeException("商家绑定不存在: " + merchantId);
        }
        MerchantAgentBinding binding = existing.get();
        binding.setEnabled(enabled);
        bindingMapper.updateById(binding);
        redisCache.deleteKey(CACHE_PREFIX + merchantId);
        log.info("切换商家绑定状态: merchantId={}, enabled={}", merchantId, enabled);
        return binding;
    }
}
