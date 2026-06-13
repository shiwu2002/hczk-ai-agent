package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.mapper.RechargeRecordMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingRecordMapper billingRecordMapper;
    private final RechargeRecordMapper rechargeRecordMapper;
    private final UserMapper userMapper;

    @Override
    public List<BillingRecord> getUserBillingRecords(Long userId) {
        return billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .orderByDesc(BillingRecord::getCreatedAt));
    }

    @Override
    public List<BillingRecord> getAllBillingRecords() {
        return billingRecordMapper.selectList(null);
    }

    @Override
    @Transactional
    public RechargeRecord recharge(Long userId, BigDecimal amount, String paymentMethod) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setBalance(user.getBalance().add(amount));
        userMapper.updateById(user);

        RechargeRecord record = new RechargeRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPaymentMethod(paymentMethod);
        record.setTransactionId("TRX" + System.currentTimeMillis());
        rechargeRecordMapper.insert(record);

        BillingRecord billing = new BillingRecord();
        billing.setUserId(userId);
        billing.setType(BillingType.RECHARGE);
        billing.setAmount(amount);
        billing.setBalanceAfter(user.getBalance());
        billing.setDetail(paymentMethod + "充值");
        billingRecordMapper.insert(billing);

        return record;
    }

    @Override
    @Transactional
    public boolean deductBalance(Long userId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getBalance().compareTo(amount) < 0) {
            return false;
        }

        user.setBalance(user.getBalance().subtract(amount));
        user.setTotalUsageTokens(user.getTotalUsageTokens() + inputTokens + outputTokens);
        userMapper.updateById(user);

        BillingRecord record = new BillingRecord();
        record.setUserId(userId);
        record.setType(BillingType.TOKEN_USAGE);
        record.setAmount(amount.negate());
        record.setBalanceAfter(user.getBalance());
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setDetail(detail);
        billingRecordMapper.insert(record);

        return true;
    }

    @Override
    public BigDecimal getUserBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user.getBalance();
    }
}
