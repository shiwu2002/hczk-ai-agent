-- ============================================================
-- 桓宸智科AI平台 - 数据库初始化脚本
-- 数据库版本：MySQL 8.0+
-- 字符集：utf8mb4 完整emoji支持
-- 排序规则：utf8mb4_unicode_ci
-- 作者：桓宸智科
-- ============================================================

-- 1. 创建数据库不存在则新建
CREATE DATABASE IF NOT EXISTS hczk_ai_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 切换业务库
USE hczk_ai_platform;

-- ============================================================
-- 数据表结构定义区域
-- ============================================================

-- ------------------------------
-- 表1：用户表 users
-- 存储平台登录用户、余额、角色、用量信息
-- 主键 user_id 使用雪花算法生成（String 类型，由 MyBatis-Plus @TableId(type=ASSIGN_ID) 赋值）
-- ------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    user_id             VARCHAR(32)    NOT NULL COMMENT '用户唯一标识（雪花算法生成，主键）'
    PRIMARY KEY,
    username            VARCHAR(50)    NOT NULL COMMENT '登录用户名',
    password            VARCHAR(255)   NOT NULL COMMENT 'BCrypt加密密码',
    email               VARCHAR(100)   NOT NULL COMMENT '绑定邮箱',
    phone_number        VARCHAR(20)         COMMENT '联系手机号',
    company_name        VARCHAR(100)        COMMENT '所属公司名称',
    role                TINYINT        NOT NULL DEFAULT 1 COMMENT '角色：0管理员 / 1普通用户',
    balance             DECIMAL(19,4)  NOT NULL DEFAULT 0.0000 COMMENT '账户余额(元)',
    total_usage_tokens  BIGINT         NOT NULL DEFAULT 0 COMMENT '累计消耗Token总量',
    status              TINYINT        NOT NULL DEFAULT 0 COMMENT '账号状态：0正常 / 1禁用',
    deleted             TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 / 1已删除',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 唯一索引
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    -- 业务查询索引
    KEY idx_role (role),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台用户信息表';

-- ------------------------------
-- 表2：AI模型配置表 ai_models
-- 第三方大模型接入配置、计价、接口地址管理
-- ------------------------------
DROP TABLE IF EXISTS ai_models;
CREATE TABLE ai_models (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL COMMENT '模型展示名称',
    provider            VARCHAR(100)   NOT NULL COMMENT '模型厂商/服务商',
    provider_type       VARCHAR(30)    NOT NULL DEFAULT 'OPENAI_COMPATIBLE' COMMENT '接口类型：OPENAI_COMPATIBLE / ANTHROPIC / MODELSCOPE',
    model_id            VARCHAR(100)   NOT NULL COMMENT '接口调用标识ID',
    status              TINYINT        NOT NULL DEFAULT 0 COMMENT '启用状态：0启用 / 1停用',
    api_base            VARCHAR(255)        COMMENT 'API接口根地址',
    api_key             VARCHAR(512)        COMMENT '服务商密钥',
    max_tokens          INT                 COMMENT '模型最大上下文长度',
    thinking            TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否启用深度思考',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_model_id (model_id),
    KEY idx_status (status),
    KEY idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方AI模型配置表';

-- ------------------------------
-- 表3：智能体表 agents
-- 注册的容器智能体，提供健康检测和对话接口
-- ------------------------------
DROP TABLE IF EXISTS agents;
CREATE TABLE agents (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL COMMENT '智能体名称',
    description         TEXT                COMMENT '智能体功能描述',
    agent_type          VARCHAR(50)         COMMENT '智能体类型标识（自定义，如 customer_service）',
    health_endpoint     VARCHAR(256)   NOT NULL COMMENT '健康检测接口地址（GET 请求）',
    chat_endpoint       VARCHAR(256)   NOT NULL COMMENT '对话接口地址（POST 请求）',
    stream_endpoint     VARCHAR(256)        COMMENT '流式对话接口地址（POST SSE，可选）',
    document_endpoint   VARCHAR(256)        COMMENT '文档上传接口地址（POST multipart/form-data，可选）',
    info_endpoint       VARCHAR(256)        COMMENT '智能体元信息接口地址（GET，可选）',
    history_endpoint    VARCHAR(256)        COMMENT '会话历史接口地址（GET/DELETE，可选）',
    auth_header         VARCHAR(256)        COMMENT '调用接口时的认证头',
    version             VARCHAR(32)         COMMENT '智能体服务版本号',
    user_id             VARCHAR(32)         COMMENT '注册用户ID(users.user_id)',
    status              TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0启用 / 1停用',
    total_calls         BIGINT         NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    total_tokens        BIGINT         NOT NULL DEFAULT 0 COMMENT '累计消耗Token',
    avg_latency         INT                 COMMENT '平均响应延迟(毫秒ms)',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    KEY idx_agent_type (agent_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='容器智能体注册表';

-- ------------------------------
-- 表4：用户API密钥表 api_keys
-- 对外开放调用平台能力的鉴权密钥
-- ------------------------------
DROP TABLE IF EXISTS api_keys;
CREATE TABLE api_keys (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL COMMENT '密钥备注名称',
    api_key             VARCHAR(512)   NOT NULL COMMENT '随机生成密钥串',
    user_id             VARCHAR(32)    NOT NULL COMMENT '归属用户ID(users.user_id)',
    model_ids           JSON                COMMENT '绑定的大模型ID列表',
    unit_price          DECIMAL(19,6)  NOT NULL DEFAULT 0.000000 COMMENT '统一Token单价（元/千Tokens）',
    total_calls         BIGINT         NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    total_input_tokens  BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输入Token数量',
    total_output_tokens BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输出Token数量',
    total_cost          DECIMAL(19,6)  NOT NULL DEFAULT 0.000000 COMMENT '累计消耗费用(元)',
    status              TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0可用 / 1禁用',
    last_used_at        DATETIME            COMMENT '最后调用时间',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_api_key (api_key),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户对外开放API密钥表';

-- ------------------------------
-- 表5：第三方渠道平台配置 platform_configs
-- 美团、抖音等渠道对接配置，绑定智能体自动回复
-- ------------------------------
DROP TABLE IF EXISTS platform_configs;
CREATE TABLE platform_configs (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    platform_type       VARCHAR(20)    NOT NULL COMMENT '渠道类型：MEITUAN美团 / DOUYIN抖音',
    app_id              VARCHAR(255)        COMMENT '渠道应用ID',
    app_secret          VARCHAR(255)        COMMENT '渠道应用密钥',
    webhook_url         VARCHAR(255)        COMMENT '渠道消息推送回调地址',
    auto_reply          BOOLEAN        NOT NULL DEFAULT FALSE COMMENT '是否开启自动AI回复',
    enabled             BOOLEAN        NOT NULL DEFAULT FALSE COMMENT '渠道整体开关启用',
    agent_id            BIGINT              COMMENT '绑定回复智能体ID(agents.id)',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_platform_type (platform_type),
    KEY idx_agent_id (agent_id),
    KEY idx_enabled (enabled),
    CONSTRAINT fk_platform_agent FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='第三方渠道对接配置表';

-- ------------------------------
-- 表6：计费流水记录表 billing_records
-- 每一笔Token消耗、充值扣账明细流水
-- ------------------------------
DROP TABLE IF EXISTS billing_records;
CREATE TABLE billing_records (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    user_id             BIGINT         NOT NULL COMMENT '操作用户ID(users.id)',
    api_key_id          BIGINT              COMMENT '关联API Key ID(api_keys.id)，API Key调用时记录',
    model_id            BIGINT              COMMENT '调用的模型ID',
    type                VARCHAR(20)    NOT NULL COMMENT '账单类型：TOKEN_USAGE消耗扣费 / RECHARGE充值入账',
    amount              DECIMAL(19,4)       COMMENT '变动金额(正充值/负扣费)',
    balance_after       DECIMAL(19,4)       COMMENT '操作后账户余额',
    input_tokens        BIGINT              COMMENT '本次输入Token数量',
    output_tokens       BIGINT              COMMENT '本次输出Token数量',
    detail              VARCHAR(500)        COMMENT '业务备注详情',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账单生成时间',

    KEY idx_user_id (user_id),
    KEY idx_api_key_id (api_key_id),
    KEY idx_type (type),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_billing_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费流水明细记录表';

-- ------------------------------
-- 表7：充值订单记录表 recharge_records
-- 用户线下/线上充值订单，记录支付状态、赠送金额
-- ------------------------------
DROP TABLE IF EXISTS recharge_records;
CREATE TABLE recharge_records (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    user_id             VARCHAR(32)    NOT NULL COMMENT '充值用户ID(users.user_id)',
    amount              DECIMAL(19,4)  NOT NULL COMMENT '实际支付金额',
    bonus_amount        DECIMAL(19,4)  NOT NULL DEFAULT 0.0000 COMMENT '平台赠送余额',
    payment_method      VARCHAR(50)         COMMENT '支付渠道：alipay支付宝 / wechat微信',
    transaction_id      VARCHAR(100)        COMMENT '第三方支付流水号',
    status              VARCHAR(20)    NOT NULL DEFAULT 'success' COMMENT '订单状态：success成功 / pending待支付 / failed失败',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',

    KEY idx_user_id (user_id),
    KEY idx_transaction_id (transaction_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户充值订单记录表';

-- ------------------------------
-- 表8：对话记录表 chat_logs
-- 存储用户每次调用的输入输出内容，用于审计和统计
-- ------------------------------
DROP TABLE IF EXISTS chat_logs;
CREATE TABLE chat_logs (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    user_id             VARCHAR(32)    NOT NULL COMMENT '用户ID(users.user_id)',
    api_key_id          BIGINT              COMMENT '关联API Key ID(api_keys.id)',
    model_id            BIGINT              COMMENT '使用的模型ID(ai_models.id)',
    model_name          VARCHAR(100)        COMMENT '模型名称',
    input_content       TEXT                COMMENT '用户输入内容',
    output_content      MEDIUMTEXT          COMMENT 'AI输出内容',
    input_tokens        BIGINT              COMMENT '输入Token数量',
    output_tokens       BIGINT              COMMENT '输出Token数量',
    cost                DECIMAL(19,6)  NOT NULL DEFAULT 0.000000 COMMENT '本次费用(元)',
    duration_ms         BIGINT              COMMENT '响应耗时(毫秒)',
    status              VARCHAR(20)    NOT NULL DEFAULT 'success' COMMENT '状态：success成功 / failed失败',
    error_message       VARCHAR(500)        COMMENT '失败时的错误信息',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_user_id (user_id),
    KEY idx_api_key_id (api_key_id),
    KEY idx_model_id (model_id),
    KEY idx_created_at (created_at),
    KEY idx_status (status),
    CONSTRAINT fk_chatlog_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话内容记录表';

-- ------------------------------
-- 表9：技能包注册表 skill
-- 存储 Skill 配置包定义，用于智能体运行时调度
-- ------------------------------
DROP TABLE IF EXISTS skill;
CREATE TABLE skill (
    id                  VARCHAR(64)    PRIMARY KEY COMMENT '技能包ID(UUID)',
    name                VARCHAR(128)   NOT NULL COMMENT '技能包名称',
    category            VARCHAR(32)         COMMENT '分类：customer_service / sales / faq / complaint / guide / custom',
    version             VARCHAR(32)    NOT NULL DEFAULT '1.0.0' COMMENT '版本号',
    description         TEXT                COMMENT '技能包描述',
    persona             JSON           NOT NULL COMMENT '人设定义 JSON：{name, systemPrompt, tone, instructions}',
    capabilities        JSON           NOT NULL COMMENT '能力定义 JSON：{tools, knowledgeRetrieval, collectionName, maxToolIterations}',
    workflow            JSON           NOT NULL COMMENT '执行流程 DAG JSON：{entry, nodes[], edges[]}',
    config              JSON           NOT NULL COMMENT '运行时配置 JSON：{maxIterations, toolTimeoutMs, toolMaxRetries, contextWindowSize, temperature}',
    status              VARCHAR(20)    NOT NULL DEFAULT 'draft' COMMENT '状态：active启用 / inactive停用 / draft草稿',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_status (status),
    KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能包注册表';

-- ------------------------------
-- 表10：商家智能体绑定表 merchant_agent_binding
-- 核心路由表，支持平台智能体绑定、Skill 模式和 Endpoint 模式
-- ------------------------------
DROP TABLE IF EXISTS merchant_agent_binding;
CREATE TABLE merchant_agent_binding (
    id                      BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    merchant_id             VARCHAR(64)    NOT NULL COMMENT '商家ID',
    agent_id                BIGINT              COMMENT '绑定的平台注册智能体ID（优先级最高）',
    skill_id                VARCHAR(64)         COMMENT 'Skill模式：绑定的技能包ID',
    agent_endpoint          VARCHAR(256)        COMMENT 'Endpoint模式：定制智能体地址',
    agent_auth_header       VARCHAR(256)        COMMENT 'Endpoint模式：认证头',
    persona_override        JSON                COMMENT '覆盖人设配置',
    capabilities_override   JSON                COMMENT '覆盖能力配置',
    config_override         JSON                COMMENT '覆盖运行时配置',
    tools_config            JSON                COMMENT '商家专属工具配置',
    enabled                 BOOLEAN        NOT NULL DEFAULT TRUE COMMENT '是否启用',
    created_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_merchant (merchant_id),
    KEY idx_agent_id (agent_id),
    KEY idx_skill_id (skill_id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家智能体绑定表';

-- ------------------------------
-- 表11：会话表 session
-- 用户对话会话记录
-- ------------------------------
DROP TABLE IF EXISTS session;
CREATE TABLE session (
    id                  VARCHAR(64)    PRIMARY KEY COMMENT '会话ID',
    skill_id            VARCHAR(64)         COMMENT '关联技能包ID',
    merchant_id         VARCHAR(64)    NOT NULL COMMENT '商家ID',
    user_id             VARCHAR(64)         COMMENT '用户ID',
    channel             VARCHAR(32)    NOT NULL DEFAULT 'web' COMMENT '渠道',
    status              VARCHAR(20)    NOT NULL DEFAULT 'active' COMMENT '状态：active进行中 / closed已关闭 / timeout超时',
    message_count       INT            NOT NULL DEFAULT 0 COMMENT '消息数量',
    started_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    ended_at            DATETIME             COMMENT '结束时间',

    KEY idx_merchant_id (merchant_id),
    KEY idx_status (status),
    KEY idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话记录表';

-- ------------------------------
-- 表12：消息表 message
-- 会话消息记录
-- ------------------------------
DROP TABLE IF EXISTS message;
CREATE TABLE message (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    session_id          VARCHAR(64)    NOT NULL COMMENT '会话ID',
    role                VARCHAR(20)    NOT NULL COMMENT '角色：system / user / assistant / tool',
    content             TEXT           NOT NULL COMMENT '消息内容',
    metadata            JSON                COMMENT '元数据',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_session_id (session_id),
    time_window_sec     INT            NOT NULL DEFAULT 60 COMMENT '时间窗口(秒)',

    KEY idx_skill_id (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流规则表';

-- ------------------------------
-- 表15：审计日志表 audit_log
-- ------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    operator_type       VARCHAR(20)    NOT NULL COMMENT '操作者类型：platform_admin / merchant / system',
    operator_id         VARCHAR(64)         COMMENT '操作者ID',
    action              VARCHAR(64)    NOT NULL COMMENT '操作动作',
    target_type         VARCHAR(32)         COMMENT '目标类型',
    target_id           VARCHAR(64)         COMMENT '目标ID',
    detail              JSON                COMMENT '操作详情',
    ip_address          VARCHAR(64)         COMMENT 'IP地址',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_target (target_type, target_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ------------------------------
-- 表16：商家余额表 merchant_balance
-- ------------------------------
DROP TABLE IF EXISTS merchant_balance;
CREATE TABLE merchant_balance (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    merchant_id         VARCHAR(64)    NOT NULL COMMENT '商家ID',
    balance             DECIMAL(12,4)  NOT NULL DEFAULT 0 COMMENT '余额',
    frozen_balance      DECIMAL(12,4)  NOT NULL DEFAULT 0 COMMENT '冻结余额',
    total_recharge      DECIMAL(12,4)  NOT NULL DEFAULT 0 COMMENT '累计充值',
    total_consumed      DECIMAL(12,4)  NOT NULL DEFAULT 0 COMMENT '累计消费',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家余额表';

-- ------------------------------
-- 表17：商家交易流水表 merchant_transaction
-- ------------------------------
DROP TABLE IF EXISTS merchant_transaction;
CREATE TABLE merchant_transaction (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    merchant_id         VARCHAR(64)    NOT NULL COMMENT '商家ID',
    type                VARCHAR(20)    NOT NULL COMMENT '类型：recharge / consume / refund / freeze / unfreeze',
    amount              DECIMAL(12,4)  NOT NULL COMMENT '金额',
    balance_after       DECIMAL(12,4)  NOT NULL COMMENT '操作后余额',
    related_usage_id    BIGINT              COMMENT '关联用量记录ID',
    description         VARCHAR(256)        COMMENT '描述',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_merchant_id (merchant_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家交易流水表';
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库归属关系表';

-- ============================================================
-- 初始化基础测试/默认数据
-- ============================================================

-- 1. 内置管理员、普通测试用户
-- 密码明文均为 admin123 / user123，已BCrypt加密
INSERT INTO users (user_id, username, password, email, phone_number, company_name, role, balance, total_usage_tokens, status)
VALUES
('U1A2B3C4D5E6F7G8', 'admin', '$2a$10$1C8bV3BolSA88HKxu4NXnOOY9ieaYwCpezFznlaNVP6OqqxjW0V42', 'admin@hczk.com', '13800000001', '桓宸智科', 0, 10000.0000, 0, 0),
('U9H8G7F6E5D4C3B2', 'user',  '$2a$10$1C8bV3BolSA88HKxu4NXnOOY9ieaYwCpezFznlaNVP6OqqxjW0V42', 'user@hczk.com',  '13800000002', '测试公司',  1,  1250.0000, 58000, 0);

-- 2. 预置主流大模型配置
INSERT INTO ai_models (name, provider, model_id, status, api_base, max_tokens)
VALUES
('DeepSeek-V3',      'DeepSeek',     'deepseek-chat',        0,   'https://api.deepseek.com/v1',                8192),
('ERNIE-4.0-Turbo', '百度智能云',   'ernie-4.0-turbo-8k',   0,   'https://qianfan.baidubce.com/v2',           8192),
('qwen-max',        '阿里云',       'qwen-max',             0,   'https://dashscope.aliyuncs.com/compatible-mode/v1',32768),
('GLM-4-Plus',      '智谱AI',       'glm-4-plus',           1,   'https://open.bigmodel.cn/api/paas/v4',       128000);

-- 3. 预置API Key（每个用户仅一个Key，绑定模型，设置统一单价）
-- admin用户的API Key：绑定DeepSeek-V3和qwen-max，单价0.002元/千Tokens
INSERT INTO api_keys (name, api_key, user_id, model_ids, unit_price, total_calls, total_input_tokens, total_output_tokens, total_cost, status)
VALUES
('管理员-通用Key', 'sk-hczk-admin-x1y2z3a4b5c6d7e8f9g0h1i2j3k4l5m6', 'U1A2B3C4D5E6F7G8', '[1, 3]', 0.002000, 128, 32000, 26000, 0.116000, 0);

-- user用户的API Key：绑定DeepSeek-V3、ERNIE-4.0-Turbo、qwen-max，单价0.005元/千Tokens
INSERT INTO api_keys (name, api_key, user_id, model_ids, unit_price, total_calls, total_input_tokens, total_output_tokens, total_cost, status)
VALUES
('测试用户-通用Key', 'sk-hczk-user-m1n2o3p4q5r6s7t8u9v0w1x2y3z4a5b6', 'U9H8G7F6E5D4C3B2', '[1, 2, 3]', 0.005000, 42, 8400, 6720, 0.075600, 0);

-- 4. 预置第三方渠道配置（默认关闭，需手动开启+填写密钥）
INSERT INTO platform_configs (platform_type, webhook_url, auto_reply, enabled)
VALUES
('MEITUAN', 'https://your-domain/api/webhook/meituan', FALSE, FALSE),
('DOUYIN',  'https://your-domain/api/webhook/douyin',  FALSE, FALSE);