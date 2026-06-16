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

import java.math.BigDecimal;
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
    public Result<List<ApiKey>> getApiKeysByUserId(@PathVariable String userId) {
        return Result.success(apiKeyService.getApiKeysByUserId(userId));
    }

    /**
     * 创建 API Key
     * @param userId    用户ID（雪花ID）
     * @param name      Key名称
     * @param unitPrice 统一Token单价（元/千Tokens）
     * @param modelIds  绑定的大模型ID列表
     */
    @PostMapping
    public Result<ApiKey> createApiKey(
            @RequestParam String userId,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "0") BigDecimal unitPrice,
            @RequestParam(required = false) List<Long> modelIds) {
        return Result.success(apiKeyService.createApiKey(userId, name, unitPrice, modelIds));
    }

    /**
     * 更新 API Key（名称、单价、绑定模型）
     */
    @PutMapping("/{id}")
    public Result<ApiKey> updateApiKey(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal unitPrice,
            @RequestParam(required = false) List<Long> modelIds) {
        return Result.success(apiKeyService.updateApiKey(id, name, unitPrice, modelIds));
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
