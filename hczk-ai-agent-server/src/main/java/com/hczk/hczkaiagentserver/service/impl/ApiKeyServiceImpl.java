package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.repository.ApiKeyRepository;
import com.hczk.hczkaiagentserver.repository.UserRepository;
import com.hczk.hczkaiagentserver.service.ApiKeyService;
import com.hczk.hczkaiagentserver.util.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    @Override
    public List<ApiKey> getAllApiKeys() {
        return apiKeyRepository.findAll();
    }

    @Override
    public List<ApiKey> getApiKeysByUserId(Long userId) {
        return apiKeyRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public ApiKey createApiKey(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        ApiKey key = new ApiKey();
        key.setName(name);
        key.setApiKey(ApiKeyGenerator.generateKey());
        key.setUser(user);
        return apiKeyRepository.save(key);
    }

    @Override
    @Transactional
    public void deleteApiKey(Long id) {
        apiKeyRepository.deleteById(id);
    }

    @Override
    public ApiKey getApiKeyByKey(String apiKey) {
        return apiKeyRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new RuntimeException("API Key 不存在"));
    }
}
