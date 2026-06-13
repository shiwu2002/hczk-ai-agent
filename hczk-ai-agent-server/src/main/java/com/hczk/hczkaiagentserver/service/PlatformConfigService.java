package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;

import java.util.List;

public interface PlatformConfigService {
    List<PlatformConfig> getAllConfigs();
    PlatformConfig getConfigByType(PlatformType type);
    PlatformConfig saveConfig(PlatformConfig config);
    boolean testConnection(PlatformType type);
}
