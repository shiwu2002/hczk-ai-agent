package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;

import java.util.List;
import java.util.Optional;

public interface MerchantAgentBindingService {
    MerchantAgentBinding createBinding(MerchantAgentBinding binding);
    MerchantAgentBinding updateBinding(String userId, MerchantAgentBinding binding);
    void deleteBinding(String userId);
    Optional<MerchantAgentBinding> findByUserId(String userId);
    List<MerchantAgentBinding> getAllBindings();
    MerchantAgentBinding toggleBinding(String userId, boolean enabled);
}
