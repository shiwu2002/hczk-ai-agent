package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-keys")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final BillingRecordMapper billingRecordMapper;

    @GetMapping
    public Result<List<ApiKey>> getAllApiKeys() {
        return Result.success(apiKeyService.getAllApiKeys());
    }

    @GetMapping("/user/{userId}")
    public Result<List<ApiKey>> getApiKeysByUserId(@PathVariable Long userId) {
        return Result.success(apiKeyService.getApiKeysByUserId(userId));
    }

    @PostMapping
    public Result<ApiKey> createApiKey(@RequestParam Long userId, @RequestParam String name) {
        return Result.success(apiKeyService.createApiKey(userId, name));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteApiKey(@PathVariable Long id) {
        apiKeyService.deleteApiKey(id);
        return Result.success();
    }

    /**
     * 获取指定 API Key 的使用明细（单次调用记录）
     */
    @GetMapping("/{id}/usage")
    public Result<List<BillingRecord>> getApiKeyUsage(@PathVariable Long id) {
        List<BillingRecord> records = billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getApiKeyId, id)
                        .eq(BillingRecord::getType, BillingType.TOKEN_USAGE)
                        .orderByDesc(BillingRecord::getCreatedAt));
        return Result.success(records);
    }
}
