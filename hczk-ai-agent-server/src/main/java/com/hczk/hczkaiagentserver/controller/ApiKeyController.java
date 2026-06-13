package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.ApiKey;
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
}
