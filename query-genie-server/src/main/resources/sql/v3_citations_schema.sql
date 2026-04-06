-- ============================================================
-- AIGenie 引用系统升级 - V3 数据库迁移脚本
-- 对应迭代：引用展示优化（CitationItem 持久化）
-- 创建时间：2026-04-06
-- ============================================================

USE `genie`;

-- ------------------------------------------------------------
-- 1. chat_message 表新增 citations_json 列
--    存储 Agent 消息的 CitationItem[] JSON（仅 assistant 消息有值）
--    若列已存在则跳过（适用于重复执行场景）
-- ------------------------------------------------------------
ALTER TABLE `chat_message`
    ADD COLUMN `citations_json` TEXT DEFAULT NULL
        COMMENT 'Agent 模式引用数据 JSON（List<CitationItem>），仅 assistant 消息有值';
