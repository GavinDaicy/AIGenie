-- Active: 1769914246591@@127.0.0.1@3306@genie
CREATE DATABASE IF NOT EXISTS `genie` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `genie`;

CREATE TABLE IF NOT EXISTS `genie`.`knowledge` (
  `id` VARCHAR(20) NOT NULL,
  `code` VARCHAR(20) NOT NULL COMMENT '知识库编码',
  `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '知识库描述',
  `status` INT(1) NOT NULL COMMENT '知识库状态: 0(未发布) 1(已发布修改中) 2(已发布)',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='知识库表';

CREATE INDEX idx_knowledge_code ON `genie`.`knowledge` (`code`);
CREATE INDEX idx_knowledge_name ON `genie`.`knowledge` (`name`);

CREATE TABLE IF NOT EXISTS `genie`.`kl_field` (
  `id` VARCHAR(20) NOT NULL,
  `knowledge_id` VARCHAR(20) NOT NULL COMMENT '知识库ID',
  `field_key` VARCHAR(20) NOT NULL COMMENT '字段键',
  `name` VARCHAR(100) NOT NULL COMMENT '字段名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '字段描述',
  `type` VARCHAR(50) NOT NULL COMMENT '字段类型',
  `matchable` INT(1) NOT NULL DEFAULT 0 COMMENT '字段是否可精准匹配: 0(不可匹配) 1(可匹配)',
  `full_text_searchable` INT(1) NOT NULL DEFAULT 0 COMMENT '字段是否可搜索: 0(不可搜索) 1(可搜索)',
  `semantic_searchable` INT(1) NOT NULL DEFAULT 0 COMMENT '字段是否可语义搜索: 0(不可搜索) 1(可搜索)',
  `sortable` INT(1) NOT NULL DEFAULT 0 COMMENT '字段是否可排序: 0(不可排序) 1(可排序)',
  `ord` INT(11) NOT NULL DEFAULT 0 COMMENT '字段排序',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='知识库字段表';

CREATE INDEX idx_kl_field_knowledge_id ON `genie`.`kl_field` (`knowledge_id`);

CREATE TABLE IF NOT EXISTS `genie`.`document` (
  `id` VARCHAR(20) NOT NULL,
  `name` VARCHAR(100) NOT NULL COMMENT '文档名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '文档描述',
  `origin_uri` VARCHAR(500) DEFAULT NULL COMMENT '文档原始URI',
  `status` VARCHAR(50) NOT NULL COMMENT '文档状态: UPLOADING(上传中),TOPARSE(待解析),PARSING(解析中),PARSED(解析完成),FAILED(解析失败)',
  `type` VARCHAR(50) NOT NULL COMMENT '文档类型: TEXT(文本),IMAGE(图片),VIDEO(视频),AUDIO(音频),PDF(PDF文件),EXCEL(Excel文件),WORD(Word文件),PPT(PPT文件),ZIP(ZIP文件),UNKNOWN(未知文件)',
  `category` VARCHAR(50) NOT NULL COMMENT '文档分类: UNSTRUCTURED(非结构化文件),STRUCTURED(结构化文件)',
  `object_store_path_origin` VARCHAR(500) DEFAULT NULL COMMENT '文档原始文件存储路径',
  `object_store_path_parsed` VARCHAR(500) DEFAULT NULL COMMENT '文档解析后文件存储路径',
  `knowledge_code` VARCHAR(20) NOT NULL COMMENT '知识库Code',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='文档记录表';

CREATE INDEX idx_document_name ON `genie`.`document` (`name`);

-- 智能问答会话表
CREATE TABLE IF NOT EXISTS `genie`.`chat_session` (
  `id` VARCHAR(32) NOT NULL COMMENT '会话ID',
  `title` VARCHAR(200) NOT NULL DEFAULT '新会话' COMMENT '会话标题',
  `knowledge_codes` VARCHAR(500) DEFAULT NULL COMMENT '知识库编码列表，JSON数组或逗号分隔',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='智能问答会话表';

CREATE INDEX idx_chat_session_update_time ON `genie`.`chat_session` (`update_time`);

-- 智能问答消息表
CREATE TABLE IF NOT EXISTS `genie`.`chat_message` (
  `id` VARCHAR(32) NOT NULL COMMENT '消息ID',
  `session_id` VARCHAR(32) NOT NULL COMMENT '会话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色: user / assistant',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `sources` JSON DEFAULT NULL COMMENT '引用来源，仅 assistant 有',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='智能问答消息表';

CREATE INDEX idx_chat_message_session_id ON `genie`.`chat_message` (`session_id`);
CREATE INDEX idx_chat_message_session_sort ON `genie`.`chat_message` (`session_id`, `sort_order`);

-- ============================================================
-- 升级脚本：为已有 genie 库增加分块策略与字段权重相关列
-- ============================================================

ALTER TABLE `genie`.`knowledge`
  ADD COLUMN `chunking_strategy` VARCHAR(32) NOT NULL DEFAULT 'RECURSIVE_TEXT' COMMENT '分块策略',
  ADD COLUMN `primary_content_field_key` VARCHAR(128) DEFAULT NULL COMMENT '主内容字段标识',
  ADD COLUMN `chunk_size` INT DEFAULT NULL COMMENT '文本块大小',
  ADD COLUMN `max_chunk_size` INT DEFAULT NULL COMMENT '文本块最大长度',
  ADD COLUMN `chunk_overlap` INT DEFAULT NULL COMMENT '文本块重叠大小',
  ADD COLUMN `rows_per_chunk` INT DEFAULT NULL COMMENT '每块聚合行数',
  ADD COLUMN `row_overlap` INT DEFAULT NULL COMMENT '块间行重叠数';

ALTER TABLE `genie`.`kl_field`
  ADD COLUMN `boost` DOUBLE DEFAULT NULL COMMENT '检索权重';


ALTER TABLE `genie`.`knowledge`
  ADD COLUMN `time_decay_enabled` INT(1) NOT NULL DEFAULT 0 COMMENT '是否启用时间衰减: 0(不启用) 1(启用)',
  ADD COLUMN `time_decay_field_source` VARCHAR(32) DEFAULT NULL COMMENT '时间字段来源: BUSINESS_FIELD | SYSTEM_DOC_UPDATE_TIME',
  ADD COLUMN `time_decay_field_key` VARCHAR(128) DEFAULT NULL COMMENT '业务日期字段 fieldKey（仅当来源为 BUSINESS_FIELD 时生效）',
  ADD COLUMN `time_decay_type` VARCHAR(32) DEFAULT NULL COMMENT '衰减类型: LINEAR | EXPONENTIAL | GAUSSIAN',
  ADD COLUMN `time_decay_scale` VARCHAR(32) DEFAULT NULL COMMENT '衰减尺度（如 30d/7d/86400s）',
  ADD COLUMN `time_decay_offset` VARCHAR(32) DEFAULT NULL COMMENT '衰减偏移（如 0d/3d）',
  ADD COLUMN `time_decay_decay` DOUBLE DEFAULT NULL COMMENT '衰减强度参数（如 0.5）',
  ADD COLUMN `time_decay_floor` DOUBLE DEFAULT NULL COMMENT '时间权重下限（如 0.2）',
  ADD COLUMN `time_decay_apply_threshold` DOUBLE DEFAULT NULL COMMENT '可选：仅当基础分数达到阈值时应用时间衰减',
  ADD COLUMN `time_decay_lambda` DOUBLE DEFAULT NULL COMMENT '可选：指数衰减参数 lambda（1/ms）',
  ADD COLUMN `time_decay_sigma` DOUBLE DEFAULT NULL COMMENT '可选：高斯衰减参数 sigma（ms）';

-- 为 knowledge 表增加「是否参与检索与问答」标志（已有数据默认启用）
ALTER TABLE `knowledge`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否参与检索与问答：1 启用 0 禁用' AFTER `status`;
