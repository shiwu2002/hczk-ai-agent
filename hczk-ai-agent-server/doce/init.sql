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
-- ------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    username            VARCHAR(50)    NOT NULL COMMENT '登录用户名',
    password            VARCHAR(255)   NOT NULL COMMENT 'BCrypt加密密码',
    email               VARCHAR(100)   NOT NULL COMMENT '绑定邮箱',
    phone_number        VARCHAR(20)         COMMENT '联系手机号',
    company_name        VARCHAR(100)        COMMENT '所属公司名称',
    role                VARCHAR(20)    NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN管理员 / USER普通用户',
    balance             DECIMAL(19,4)  NOT NULL DEFAULT 0.0000 COMMENT '账户余额(元)',
    total_usage_tokens  BIGINT         NOT NULL DEFAULT 0 COMMENT '累计消耗Token总量',
    status              VARCHAR(20)    NOT NULL DEFAULT 'active' COMMENT '账号状态：active正常 / inactive禁用',
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
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '启用状态：ACTIVE启用 / INACTIVE停用',
    api_base            VARCHAR(255)        COMMENT 'API接口根地址',
    api_key             VARCHAR(512)        COMMENT '服务商密钥',
    input_price         DECIMAL(19,6)       COMMENT '输入计价：元/千Tokens',
    output_price        DECIMAL(19,6)       COMMENT '输出计价：元/千Tokens',
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
-- 用户创建的自定义AI智能体，绑定模型与归属用户
-- ------------------------------
DROP TABLE IF EXISTS agents;
CREATE TABLE agents (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    name                VARCHAR(100)   NOT NULL COMMENT '智能体名称',
    description         TEXT                COMMENT '智能体功能描述、提示词配置',
    model_id            BIGINT         NOT NULL COMMENT '关联模型ID(ai_models.id)',
    user_id             BIGINT         NOT NULL COMMENT '归属用户ID(users.id)',
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用 / INACTIVE停用',
    agent_type          VARCHAR(50)         COMMENT '智能体业务类型',
    total_calls         BIGINT         NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    total_tokens        BIGINT         NOT NULL DEFAULT 0 COMMENT '累计消耗Token',
    avg_latency         INT                 COMMENT '平均响应延迟(毫秒ms)',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    KEY idx_user_id (user_id),
    KEY idx_model_id (model_id),
    KEY idx_status (status),
    KEY idx_agent_type (agent_type),
    -- 外键约束
    CONSTRAINT fk_agent_model FOREIGN KEY (model_id) REFERENCES ai_models(id) ON DELETE RESTRICT,
    CONSTRAINT fk_agent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自定义智能体表';

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
    user_id             BIGINT         NOT NULL COMMENT '归属用户ID(users.id)',
    total_calls         BIGINT         NOT NULL DEFAULT 0 COMMENT '累计调用次数',
    total_input_tokens  BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输入Token数量',
    total_output_tokens BIGINT         NOT NULL DEFAULT 0 COMMENT '累计输出Token数量',
    total_cost          DECIMAL(19,6)  NOT NULL DEFAULT 0.000000 COMMENT '累计消耗费用(元)',
    status              VARCHAR(20)    NOT NULL DEFAULT 'active' COMMENT '状态：active可用 / inactive禁用',
    last_used_at        DATETIME            COMMENT '最后调用时间',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    UNIQUE KEY uk_api_key (api_key),
    KEY idx_user_id (user_id),
    KEY idx_status (status),
    CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
    user_id             BIGINT         NOT NULL COMMENT '充值用户ID(users.id)',
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
    CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户充值订单记录表';

-- ------------------------------
-- 表8：对话记录表 chat_logs
-- 存储用户每次调用的输入输出内容，用于审计和统计
-- ------------------------------
DROP TABLE IF EXISTS chat_logs;
CREATE TABLE chat_logs (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    user_id             BIGINT         NOT NULL COMMENT '用户ID(users.id)',
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
    CONSTRAINT fk_chatlog_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
-- 核心路由表，支持 Skill 模式和 Endpoint 模式
-- ------------------------------
DROP TABLE IF EXISTS merchant_agent_binding;
CREATE TABLE merchant_agent_binding (
    id                      BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    merchant_id             VARCHAR(64)    NOT NULL COMMENT '商家ID',
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
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录表';

-- ------------------------------
-- 表13：技能包调用计量表 skill_usage
-- ------------------------------
DROP TABLE IF EXISTS skill_usage;
CREATE TABLE skill_usage (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    skill_id            VARCHAR(64)         COMMENT '技能包ID',
    merchant_id         VARCHAR(64)    NOT NULL COMMENT '商家ID',
    user_id             VARCHAR(64)         COMMENT '用户ID',
    session_id          VARCHAR(64)         COMMENT '会话ID',
    call_type           VARCHAR(20)    NOT NULL COMMENT '调用类型：chat / stream / tool / knowledge',
    input_tokens        BIGINT         NOT NULL DEFAULT 0 COMMENT '输入Token数',
    output_tokens       BIGINT         NOT NULL DEFAULT 0 COMMENT '输出Token数',
    tool_calls_count    INT            NOT NULL DEFAULT 0 COMMENT '工具调用次数',
    retrieval_count     INT            NOT NULL DEFAULT 0 COMMENT '知识检索次数',
    billing_type        VARCHAR(32)         COMMENT '计费类型',
    cost                DECIMAL(10,4)  NOT NULL DEFAULT 0 COMMENT '费用',
    duration_ms         INT                 COMMENT '耗时(毫秒)',
    status              VARCHAR(20)    NOT NULL COMMENT '状态：success / error / timeout',
    error_message       TEXT                COMMENT '错误信息',
    called_at           DATETIME       NOT NULL COMMENT '调用时间',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_skill_id (skill_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_called_at (called_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能包调用计量表';

-- ------------------------------
-- 表14：限流规则表 skill_rate_limit
-- ------------------------------
DROP TABLE IF EXISTS skill_rate_limit;
CREATE TABLE skill_rate_limit (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    skill_id            VARCHAR(64)    NOT NULL COMMENT '技能包ID',
    limit_type          VARCHAR(20)    NOT NULL COMMENT '限流类型：rpm / tpm / concurrent',
    limit_value         INT            NOT NULL COMMENT '限流值',
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

-- ------------------------------
-- 表18：技能包计费规则表 skill_pricing
-- ------------------------------
DROP TABLE IF EXISTS skill_pricing;
CREATE TABLE skill_pricing (
    id                      BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    skill_id                VARCHAR(64)    NOT NULL COMMENT '技能包ID',
    billing_type            VARCHAR(20)    NOT NULL COMMENT '计费类型：per_call / per_token / per_session / monthly',
    price_per_call          DECIMAL(10,4)       COMMENT '每次调用价格',
    price_per_input_token   DECIMAL(10,6)       COMMENT '输入Token价格',
    price_per_output_token  DECIMAL(10,6)       COMMENT '输出Token价格',
    price_per_session       DECIMAL(10,4)       COMMENT '每会话价格',
    monthly_price           DECIMAL(10,2)       COMMENT '月套餐价格',
    monthly_included_calls  INT                 COMMENT '月套餐包含调用次数',
    free_calls_per_month    INT            NOT NULL DEFAULT 0 COMMENT '每月免费调用次数',
    effective_from          DATETIME       NOT NULL COMMENT '生效时间',
    effective_to            DATETIME             COMMENT '失效时间',
    created_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    KEY idx_skill_id (skill_id),
    KEY idx_effective_from (effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能包计费规则表';

-- ------------------------------
-- 表19：知识库归属表 knowledge_bases
-- 记录知识库与智能体/用户的归属关系
-- ------------------------------
DROP TABLE IF EXISTS knowledge_bases;
CREATE TABLE knowledge_bases (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    name                VARCHAR(128)   NOT NULL COMMENT '知识库名称(Milvus collection名)',
    description         VARCHAR(500)        COMMENT '知识库描述',
    owner_type          VARCHAR(20)    NOT NULL DEFAULT 'AGENT' COMMENT '归属类型：AGENT智能体 / USER用户',
    owner_id            BIGINT         NOT NULL COMMENT '归属对象ID(agents.id或users.id)',
    agent_id            VARCHAR(64)    NOT NULL COMMENT 'Milvus集合命名用的agentId字符串',
    collection_name     VARCHAR(128)   NOT NULL DEFAULT 'default' COMMENT 'Milvus子集合名',
    row_count           BIGINT         NOT NULL DEFAULT 0 COMMENT '文档条数缓存',
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE启用 / INACTIVE停用',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_owner_collection (owner_type, owner_id, collection_name),
    KEY idx_owner_type (owner_type),
    KEY idx_agent_id (agent_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库归属关系表';

-- ============================================================
-- 初始化基础测试/默认数据
-- ============================================================

-- 1. 内置管理员、普通测试用户
-- 密码明文均为 admin123 / user123，已BCrypt加密
INSERT INTO users (username, password, email, role, balance)
VALUES
('admin', '$2a$10$1C8bV3BolSA88HKxu4NXnOOY9ieaYwCpezFznlaNVP6OqqxjW0V42', 'admin@hczk.com', 'ADMIN', 10000.0000),
('user',  '$2a$10$1C8bV3BolSA88HKxu4NXnOOY9ieaYwCpezFznlaNVP6OqqxjW0V42', 'user@hczk.com',  'USER',  1250.0000);

-- 2. 预置主流大模型配置
INSERT INTO ai_models (name, provider, model_id, status, api_base, input_price, output_price, max_tokens)
VALUES
('DeepSeek-V3',      'DeepSeek',     'deepseek-chat',        'ACTIVE',   'https://api.deepseek.com/v1',                0.001000, 0.002000, 8192),
('ERNIE-4.0-Turbo', '百度智能云',   'ernie-4.0-turbo-8k',   'ACTIVE',   'https://qianfan.baidubce.com/v2',           0.008000, 0.008000, 8192),
('qwen-max',        '阿里云',       'qwen-max',             'ACTIVE',   'https://dashscope.aliyuncs.com/compatible-mode/v1',0.020000,0.020000,32768),
('GLM-4-Plus',      '智谱AI',       'glm-4-plus',           'INACTIVE', 'https://open.bigmodel.cn/api/paas/v4',       0.010000, 0.010000, 128000);

-- 3. 预置第三方渠道配置（默认关闭，需手动开启+填写密钥）
INSERT INTO platform_configs (platform_type, webhook_url, auto_reply, enabled)
VALUES
('MEITUAN', 'https://your-domain/api/webhook/meituan', FALSE, FALSE),
('DOUYIN',  'https://your-domain/api/webhook/douyin',  FALSE, FALSE);