-- ============================================================
-- AIGenie 字符集升级 - V4 数据库迁移脚本
-- 将早期建表使用 utf8（3字节）的表全部转为 utf8mb4（4字节），
-- 以支持 Emoji 及其他辅助平面字符。
-- 创建时间：2026-04-06
-- ============================================================

USE `genie`;

-- ------------------------------------------------------------
-- 1. knowledge 表
-- ------------------------------------------------------------
ALTER TABLE `knowledge`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 2. kl_field 表
-- ------------------------------------------------------------
ALTER TABLE `kl_field`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 3. document 表
-- ------------------------------------------------------------
ALTER TABLE `document`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 4. chat_session 表
-- ------------------------------------------------------------
ALTER TABLE `chat_session`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 5. chat_message 表
-- ------------------------------------------------------------
ALTER TABLE `chat_message`
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 6. 同时修改数据库默认字符集，确保后续新建表继承 utf8mb4
-- ------------------------------------------------------------
ALTER DATABASE `genie`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
