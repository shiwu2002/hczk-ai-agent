-- =============================================================
-- 迁移脚本：v9 → v10 — Skill 可见性与用户绑定
-- 日期：2026-06-17
-- 说明：
--   1) skill 表新增 visibility 字段：public（公开，所有智能体可用）/ private（私有，需绑定用户）
--   2) 新建 user_skill_binding 表：用户与私有 Skill 的多对多绑定关系
--   3) 智能体调用 skills 时，只能查看和调用 public skills + 自身用户绑定的 private skills
--
-- 兼容性：使用 INFORMATION_SCHEMA + PREPARE stmt 实现幂等，兼容 MySQL 8.0
-- =============================================================

USE hczk_ai_platform;

-- =============================================================
-- 0. 安全添加列的存储过程（可重复执行）
-- =============================================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl_name VARCHAR(128),
    IN col_name VARCHAR(128),
    IN col_def VARCHAR(512),
    IN after_col VARCHAR(128)
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = col_name;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl_name, ' ADD COLUMN ', col_name, ' ', col_def, ' AFTER ', after_col);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已添加列: ', col_name) AS msg;
    ELSE
        SELECT CONCAT('列已存在，跳过: ', col_name) AS msg;
    END IF;
END //
DELIMITER ;

-- =============================================================
-- 1. skill 表新增 visibility 字段
--    public  — 公开，所有智能体可调用
--    private — 私有，仅被绑定的用户对应的智能体可调用
-- =============================================================
CALL add_column_if_not_exists('skill', 'visibility',
    "VARCHAR(20) NOT NULL DEFAULT 'public' COMMENT '可见性：public公开 / private私有（需绑定用户）'",
    'status');

-- 将现有 skill 默认设为 public（仅对未设置 visibility 的行生效）
UPDATE skill SET visibility = 'public' WHERE visibility IS NULL OR visibility = '';

DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- =============================================================
-- 2. 新建 user_skill_binding 表（用户与私有 Skill 的绑定关系）
-- =============================================================
CREATE TABLE IF NOT EXISTS user_skill_binding (
    id              BIGINT AUTO_INCREMENT COMMENT '主键ID'
        PRIMARY KEY,
    user_id         VARCHAR(32)    NOT NULL COMMENT '用户ID（users.user_id 雪花ID字符串）',
    skill_id        VARCHAR(64)    NOT NULL COMMENT '工具组ID（skill.id）',
    enabled         TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '是否启用：1启用 / 0禁用',
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_user_skill (user_id, skill_id),
    KEY idx_user_id (user_id),
    KEY idx_skill_id (skill_id),
    CONSTRAINT fk_usb_user  FOREIGN KEY (user_id)  REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_usb_skill FOREIGN KEY (skill_id) REFERENCES skill(id)      ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与私有工具组绑定关系表';

-- =============================================================
-- 3. 验证
-- =============================================================
SELECT '=== skill 表 visibility 字段验证 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'skill' AND COLUMN_NAME = 'visibility';

SELECT '=== user_skill_binding 表结构验证 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'user_skill_binding'
ORDER BY ORDINAL_POSITION;
