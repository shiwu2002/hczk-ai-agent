package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import com.hczk.hczkaiagentserver.mapper.PlatformConfigMapper;
import com.hczk.hczkaiagentserver.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final PlatformConfigMapper platformConfigMapper;

    @Override
    public List<PlatformConfig> getAllConfigs() {
        return platformConfigMapper.selectList(null);
    }

    @Override
    public PlatformConfig getConfigByType(PlatformType type) {
        PlatformConfig config = platformConfigMapper.selectOne(
                new LambdaQueryWrapper<PlatformConfig>().eq(PlatformConfig::getPlatformType, type));
        if (config == null) {
            throw new RuntimeException("平台配置不存在");
        }
        return config;
    }

    @Override
    @Transactional
    public PlatformConfig saveConfig(PlatformConfig config) {
        if (config.getId() == null) {
            platformConfigMapper.insert(config);
        } else {
            platformConfigMapper.updateById(config);
        }
        return config;
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
