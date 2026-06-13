package com.hczk.hczkaiagentserver.service.impl;

import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import com.hczk.hczkaiagentserver.repository.PlatformConfigRepository;
import com.hczk.hczkaiagentserver.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final PlatformConfigRepository platformConfigRepository;

    @Override
    public List<PlatformConfig> getAllConfigs() {
        return platformConfigRepository.findAll();
    }

    @Override
    public PlatformConfig getConfigByType(PlatformType type) {
        return platformConfigRepository.findByPlatformType(type)
                .orElseThrow(() -> new RuntimeException("平台配置不存在"));
    }

    @Override
    @Transactional
    public PlatformConfig saveConfig(PlatformConfig config) {
        return platformConfigRepository.save(config);
    }

    @Override
    public boolean testConnection(PlatformType type) {
        PlatformConfig config = getConfigByType(type);
        if (config.getAppId() == null || config.getAppId().isEmpty()) {
            return false;
        }
        return true;
    }
}
