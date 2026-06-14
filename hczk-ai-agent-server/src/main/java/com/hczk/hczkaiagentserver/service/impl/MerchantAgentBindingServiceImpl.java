package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;
import com.hczk.hczkaiagentserver.mapper.MerchantAgentBindingMapper;
import com.hczk.hczkaiagentserver.service.MerchantAgentBindingService;
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

    @Override
    @Transactional
    public MerchantAgentBinding createBinding(MerchantAgentBinding binding) {
        Optional<MerchantAgentBinding> existing = findByUserId(binding.getUserId());
        if (existing.isPresent()) {
            throw new RuntimeException("用户已存在绑定: " + binding.getUserId());
        }
        
        if (binding.getSkillId() == null && binding.getAgentEndpoint() == null) {
            throw new RuntimeException("必须提供 skill_id 或 agent_endpoint");
        }
        
        if (binding.getSkillId() != null && binding.getAgentEndpoint() != null) {
            throw new RuntimeException("skill_id 和 agent_endpoint 不能同时提供");
        }
        
        bindingMapper.insert(binding);
        log.info("创建用户绑定: userId={}, skillId={}, endpoint={}", 
                binding.getUserId(), binding.getSkillId(), binding.getAgentEndpoint());
        return binding;
    }

    @Override
    @Transactional
    public MerchantAgentBinding updateBinding(Long userId, MerchantAgentBinding binding) {
        Optional<MerchantAgentBinding> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            throw new RuntimeException("用户绑定不存在: " + userId);
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
        log.info("更新用户绑定: userId={}", userId);
        return update;
    }

    @Override
    @Transactional
    public void deleteBinding(Long userId) {
        Optional<MerchantAgentBinding> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            throw new RuntimeException("用户绑定不存在: " + userId);
        }
        bindingMapper.deleteById(existing.get().getId());
        log.info("删除用户绑定: userId={}", userId);
    }

    @Override
    public Optional<MerchantAgentBinding> findByUserId(Long userId) {
        LambdaQueryWrapper<MerchantAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantAgentBinding::getUserId, userId);
        return Optional.ofNullable(bindingMapper.selectOne(wrapper));
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
            throw new RuntimeException("用户绑定不存在: " + userId);
        }
        MerchantAgentBinding binding = existing.get();
        binding.setEnabled(enabled);
        bindingMapper.updateById(binding);
        log.info("切换用户绑定状态: userId={}, enabled={}", userId, enabled);
        return binding;
    }
}