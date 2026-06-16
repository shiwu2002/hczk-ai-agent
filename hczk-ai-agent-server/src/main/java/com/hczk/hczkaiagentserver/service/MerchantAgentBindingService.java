package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.MerchantAgentBinding;

import java.util.List;
import java.util.Optional;

public interface MerchantAgentBindingService {
    MerchantAgentBinding createBinding(MerchantAgentBinding binding);
    MerchantAgentBinding updateBinding(String merchantId, MerchantAgentBinding binding);
    void deleteBinding(String merchantId);
    Optional<MerchantAgentBinding> findByMerchantId(String merchantId);
    List<MerchantAgentBinding> getAllBindings();
    MerchantAgentBinding toggleBinding(String merchantId, boolean enabled);
}
