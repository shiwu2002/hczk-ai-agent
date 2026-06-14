package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;

import java.util.List;
import java.util.Optional;

public interface MerchantAgentBindingService {
    MerchantAgentBinding createBinding(MerchantAgentBinding binding);
    MerchantAgentBinding updateBinding(Long userId, MerchantAgentBinding binding);
    void deleteBinding(Long userId);
    Optional<MerchantAgentBinding> findByUserId(Long userId);
    List<MerchantAgentBinding> getAllBindings();
    MerchantAgentBinding toggleBinding(Long userId, boolean enabled);
}