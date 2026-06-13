package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/records/{userId}")
    public Result<List<BillingRecord>> getUserBillingRecords(@PathVariable Long userId) {
        return Result.success(billingService.getUserBillingRecords(userId));
    }

    @GetMapping("/records")
    public Result<List<BillingRecord>> getAllBillingRecords() {
        return Result.success(billingService.getAllBillingRecords());
    }

    @PostMapping("/recharge")
    public Result<RechargeRecord> recharge(@RequestParam Long userId,
                                           @RequestParam BigDecimal amount,
                                           @RequestParam String paymentMethod) {
        return Result.success(billingService.recharge(userId, amount, paymentMethod));
    }

    @GetMapping("/balance/{userId}")
    public Result<BigDecimal> getUserBalance(@PathVariable Long userId) {
        return Result.success(billingService.getUserBalance(userId));
    }
}
