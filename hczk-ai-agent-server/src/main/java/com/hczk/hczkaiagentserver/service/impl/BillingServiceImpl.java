package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.repository.BillingRecordRepository;
import com.hczk.hczkaiagentserver.repository.RechargeRecordRepository;
import com.hczk.hczkaiagentserver.repository.UserRepository;
import com.hczk.hczkaiagentserver.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingRecordRepository billingRecordRepository;
    private final RechargeRecordRepository rechargeRecordRepository;
    private final UserRepository userRepository;

    @Override
    public List<BillingRecord> getUserBillingRecords(Long userId) {
        return billingRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<BillingRecord> getAllBillingRecords() {
        return billingRecordRepository.findAll();
    }

    @Override
    @Transactional
    public RechargeRecord recharge(Long userId, BigDecimal amount, String paymentMethod) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        RechargeRecord record = new RechargeRecord();
        record.setUser(user);
        record.setAmount(amount);
        record.setPaymentMethod(paymentMethod);
        record.setTransactionId("TRX" + System.currentTimeMillis());
        RechargeRecord saved = rechargeRecordRepository.save(record);

        BillingRecord billing = new BillingRecord();
        billing.setUser(user);
        billing.setType(BillingType.RECHARGE);
        billing.setAmount(amount);
        billing.setBalanceAfter(user.getBalance());
        billing.setDetail(paymentMethod + "充值");
        billingRecordRepository.save(billing);

        return saved;
    }

    @Override
    @Transactional
    public boolean deductBalance(Long userId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (user.getBalance().compareTo(amount) < 0) {
            return false;
        }

        user.setBalance(user.getBalance().subtract(amount));
        user.setTotalUsageTokens(user.getTotalUsageTokens() + inputTokens + outputTokens);
        userRepository.save(user);

        BillingRecord record = new BillingRecord();
        record.setUser(user);
        record.setType(BillingType.TOKEN_USAGE);
        record.setAmount(amount.negate());
        record.setBalanceAfter(user.getBalance());
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setDetail(detail);
        billingRecordRepository.save(record);

        return true;
    }

    @Override
    public BigDecimal getUserBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return user.getBalance();
    }
}
