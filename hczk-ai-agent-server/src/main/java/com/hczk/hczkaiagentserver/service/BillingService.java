package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;

import java.math.BigDecimal;
import java.util.List;

public interface BillingService {
    List<BillingRecord> getUserBillingRecords(Long userId);
    List<BillingRecord> getAllBillingRecords();
    RechargeRecord recharge(Long userId, BigDecimal amount, String paymentMethod);
    boolean deductBalance(Long userId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail);
    BigDecimal getUserBalance(Long userId);
}
