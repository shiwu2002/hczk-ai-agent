package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.PlatformConfig;
import com.hczk.hczkaiagentserver.enums.PlatformType;

import java.util.List;

/**
 * 平台配置服务接口
 * 管理各AI平台（如DashScope、OpenAI等）的连接配置信息
 */
public interface PlatformConfigService {

    /** 获取所有平台配置 */
    List<PlatformConfig> getAllConfigs();

    /** 根据平台类型获取配置 */
    PlatformConfig getConfigByType(PlatformType type);

    /** 保存平台配置（新增或更新） */
    PlatformConfig saveConfig(PlatformConfig config);

    /** 测试平台连接是否可用 */
    boolean testConnection(PlatformType type);
}
