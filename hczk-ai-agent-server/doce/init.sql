-- ============================================================
-- 桓宸智科AI平台 - 数据库初始化脚本
-- 数据库：MySQL 8.0
-- 字符集：utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS hczk_ai_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hczk_ai_platform;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username            VARCHAR(50) NOT NULL COMMENT '用户名',
    password            VARCHAR(255) NOT NULL COMMENT '加密密码',
    email               VARCHAR(100) NOT NULL COMMENT '邮箱',
    phone_number        VARCHAR(20) COMMENT '手机号',
    company_name        VARCHAR(100) COMMENT '公司名称',
    role                VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
    balance             DECIMAL(19,4) NOT NULL DEFAULT 0.0000 COMMENT '账户余额',
    total_usage_tokens  BIGINT NOT NULL DEFAULT 0 COMMENT '累计消耗token数',
    status              VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    KEY idx_role (role),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. AI模型表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS ai_models;
CREATE TABLE ai_models (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name                VARCHAR(100) NOT NULL COMMENT '模型名称',
    provider            VARCHAR(100) NOT NULL COMMENT '提供商',
    model_id            VARCHAR(100) NOT NULL COMMENT '模型调用ID',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    api_base            VARCHAR(255) COMMENT 'API基础地址',
    api_key             VARCHAR(512) COMMENT '平台API密钥',
    input_price         DECIMAL(19,6) COMMENT '输入价格（元/1K tokens）',
    output_price        DECIMAL(19,6) COMMENT '输出价格（元/1K tokens）',
    max_tokens          INT COMMENT '最大上下文长度',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_model_id (model_id),
    KEY idx_status (status),
    KEY idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型表';

-- ------------------------------------------------------------
-- 3. 智能体表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS agents;
CREATE TABLE agents (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name                VARCHAR(100) NOT NULL COMMENT '智能体名称',
    description         TEXT COMMENT '智能体描述',
    model_id            BIGINT NOT NULL COMMENT '关联模型ID',
    user_id             BIGINT NOT NULL COMMENT '所属用户ID',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    agent_type          VARCHAR(50) COMMENT '智能体类型',
    total_calls         BIGINT NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    total_tokens        BIGINT NOT NULL DEFAULT 0 COMMENT '累计消耗token数',
    avg_latency         INT COMMENT '平均响应延迟(ms)',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_user_id (user_id),
    KEY idx_model_id (model_id),
    KEY idx_status (status),
    KEY idx_agent_type (agent_type),
    CONSTRAINT fk_agent_model FOREIGN KEY (model_id) REFERENCES ai_models(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体表';

-- ------------------------------------------------------------
-- 4. API密钥表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS api_keys;
CREATE TABLE api_keys (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name                VARCHAR(100) NOT NULL COMMENT '密钥名称',
    api_key             VARCHAR(512) NOT NULL COMMENT '密钥值',
    user_id             BIGINT NOT NULL COMMENT '所属用户ID',
    total_calls         BIGINT NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    status              VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/inactive',
    last_used_at        DATETIME COMMENT '最后使用时间',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_api_key (api_key),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API密钥表';

-- ------------------------------------------------------------
-- 5. 平台配置表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS platform_configs;
CREATE TABLE platform_configs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    platform_type       VARCHAR(20) NOT NULL COMMENT '平台类型：MEITUAN/DOUYIN',
    app_id              VARCHAR(255) COMMENT '应用ID',
    app_secret          VARCHAR(255) COMMENT '应用密钥',
    webhook_url         VARCHAR(255) COMMENT 'Webhook接收地址',
    auto_reply          BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否自动回复',
    enabled             BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用',
    agent_id            BIGINT COMMENT '关联智能体ID',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_platform_type (platform_type),
    KEY idx_agent_id (agent_id),
    KEY idx_enabled (enabled),
    CONSTRAINT fk_platform_agent FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台配置表';

-- ------------------------------------------------------------
-- 6. 计费记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS billing_records;
CREATE TABLE billing_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    type                VARCHAR(20) NOT NULL COMMENT '类型：TOKEN_USAGE/RECHARGE',
    amount              DECIMAL(19,4) COMMENT '金额',
    balance_after       DECIMAL(19,4) COMMENT '操作后余额',
    input_tokens        BIGINT COMMENT '输入token数量',
    output_tokens       BIGINT COMMENT '输出token数量',
    detail              VARCHAR(500) COMMENT '详情说明',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id (user_id),
    KEY idx_type (type),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_billing_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费记录表';

-- ------------------------------------------------------------
-- 7. 充值记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS recharge_records;
CREATE TABLE recharge_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    amount              DECIMAL(19,4) NOT NULL COMMENT '充值金额',
    bonus_amount        DECIMAL(19,4) NOT NULL DEFAULT 0.0000 COMMENT '赠送金额',
    payment_method      VARCHAR(50) COMMENT '支付方式：alipay/wechat',
    transaction_id      VARCHAR(100) COMMENT '交易流水号',
    status              VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '状态：success/pending/failed',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id (user_id),
    KEY idx_transaction_id (transaction_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值记录表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 默认管理员账号（密码：admin123）
INSERT INTO users (username, password, email, role, balance) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'admin@hczk.com', 'ADMIN', 10000.0000);

-- 默认普通用户（密码：user123）
INSERT INTO users (username, password, email, role, balance) VALUES
('user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO', 'user@hczk.com', 'USER', 1250.0000);

-- 默认AI模型
INSERT INTO ai_models (name, provider, model_id, status, api_base, input_price, output_price, max_tokens) VALUES
('DeepSeek-V3', 'DeepSeek', 'deepseek-chat', 'ACTIVE', 'https://api.deepseek.com/v1', 0.001000, 0.002000, 8192),
('ERNIE-4.0-Turbo', '百度智能云', 'ernie-4.0-turbo-8k', 'ACTIVE', 'https://qianfan.baidubce.com/v2', 0.008000, 0.008000, 8192),
('qwen-max', '阿里云', 'qwen-max', 'ACTIVE', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 0.020000, 0.020000, 32768),
('GLM-4-Plus', '智谱AI', 'glm-4-plus', 'INACTIVE', 'https://open.bigmodel.cn/api/paas/v4', 0.010000, 0.010000, 128000);

-- 默认平台配置（未启用）
INSERT INTO platform_configs (platform_type, webhook_url, auto_reply, enabled) VALUES
('MEITUAN', 'https://your-domain/api/webhook/meituan', FALSE, FALSE),
('DOUYIN', 'https://your-domain/api/webhook/douyin', FALSE, FALSE);
