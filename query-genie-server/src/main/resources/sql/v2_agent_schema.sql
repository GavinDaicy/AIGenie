-- ============================================================
-- AIGenie Agent 升级 - V2 数据库迁移脚本
-- 对应迭代：迭代1 - 数据层 & Schema 管理后台
-- 创建时间：2026-04-02
-- ============================================================

USE `genie`;

-- ------------------------------------------------------------
-- 1. 新增表：db_datasource（数据源注册）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_datasource` (
    `id`          BIGINT NOT NULL COMMENT '主键ID（Snowflake）',
    `name`        VARCHAR(64) NOT NULL COMMENT '数据源名称',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '数据源业务描述',
    `db_url`      VARCHAR(512) NOT NULL COMMENT 'JDBC URL',
    `db_username` VARCHAR(64) NOT NULL COMMENT '数据库用户名',
    `db_password` VARCHAR(256) NOT NULL COMMENT '数据库密码（AES加密存储）',
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源注册表';

-- ------------------------------------------------------------
-- 2. 新增表：db_table_schema（表结构元数据）
-- columns_json 格式：[{name, alias, type, description, sample_values}]
-- sample_queries 格式：[{question, sql}]
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `db_table_schema` (
    `id`             BIGINT NOT NULL COMMENT '主键ID（Snowflake）',
    `datasource_id`  BIGINT NOT NULL COMMENT '关联数据源ID',
    `table_name`     VARCHAR(128) NOT NULL COMMENT '实际表名',
    `alias`          VARCHAR(128) DEFAULT NULL COMMENT '业务别名，如"钢筋价格表"',
    `description`    VARCHAR(512) DEFAULT NULL COMMENT '表的业务说明',
    `columns_json`   JSON NOT NULL COMMENT '字段元数据列表，每项含 name/alias/type/description/sample_values',
    `sample_queries` JSON DEFAULT NULL COMMENT '示例问答对列表，每项含 question/sql，辅助 Few-shot',
    `enabled`        TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用参与 Text-to-SQL: 1=启用 0=禁用',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_datasource_table` (`datasource_id`, `table_name`),
    INDEX `idx_datasource_id` (`datasource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库表结构元数据表（Text-to-SQL Schema Registry）';

-- ------------------------------------------------------------
-- 3. 新增表：agent_step_log（Agent 步骤日志）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_step_log` (
    `id`          BIGINT NOT NULL COMMENT '主键ID（Snowflake）',
    `session_id`  VARCHAR(32) NOT NULL COMMENT '关联会话ID（chat_session.id）',
    `message_id`  VARCHAR(32) DEFAULT NULL COMMENT '关联消息ID（chat_message.id）',
    `iteration`   INT DEFAULT NULL COMMENT 'ReAct 迭代轮次（从1开始）',
    `step_type`   VARCHAR(32) DEFAULT NULL COMMENT '步骤类型: THOUGHT/TOOL_CALL/TOOL_RESULT/ASK_USER/FINAL_ANSWER/ERROR',
    `tool_name`   VARCHAR(64) DEFAULT NULL COMMENT '工具名称（仅 TOOL_CALL/TOOL_RESULT 时有值）',
    `content`     TEXT DEFAULT NULL COMMENT '步骤内容（思考文本/工具参数JSON/工具返回结果）',
    `duration_ms` INT DEFAULT NULL COMMENT '步骤执行耗时（毫秒）',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 推理步骤日志表';

-- ------------------------------------------------------------
-- 4. 新增表：agent_feedback（用户评分反馈）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_feedback` (
    `id`         BIGINT NOT NULL COMMENT '主键ID（Snowflake）',
    `message_id` VARCHAR(32) NOT NULL COMMENT '关联消息ID（chat_message.id）',
    `session_id` VARCHAR(32) NOT NULL COMMENT '关联会话ID（chat_session.id）',
    `rating`     TINYINT NOT NULL COMMENT '评分: 1=好评 -1=差评',
    `comment`    VARCHAR(1000) DEFAULT NULL COMMENT '用户反馈文字（可选）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_message_id` (`message_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 用户评分反馈表';

-- ------------------------------------------------------------
-- 5. 变更现有表：chat_session 新增 mode 字段
-- 若字段已存在则跳过（适用于重复执行场景）
-- ------------------------------------------------------------
ALTER TABLE `chat_session`
    ADD COLUMN `mode` VARCHAR(16) NOT NULL DEFAULT 'RAG' COMMENT '问答模式: RAG | AGENT' AFTER `knowledge_codes`;
