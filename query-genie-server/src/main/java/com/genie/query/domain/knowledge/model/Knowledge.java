package com.genie.query.domain.knowledge.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/6
 */
@Data
public class Knowledge {
    /** 知识库id标识 */
    private String id;
    /** 知识库编码, 全局唯一 */
    private String code;
    /** 知识库名称 */
    private String name;
    /** 知识库描述 */
    private String description;
    /** 知识库字段列表 */
    private List<KLField> fields;
    /**
     * 分块策略与参数配置。
     * 若为空则在创建知识库时会填充默认配置。
     */
    private ChunkingPolicy chunkingPolicy;
    /**
     * 主内容字段 fieldKey（可选）。
     * 当知识库包含多个字段，但其中某一字段是长文本主内容时，可通过该字段显式指定。
     */
    private String primaryContentFieldKey;
    /**
     * 时间衰减配置（可选）。
     * 若为空则表示不启用时间衰减。
     */
    private TimeDecayConfig timeDecayConfig;
    /** 知识库状态 */
    private KLState status;
    /**
     * 是否参与检索与智能问答；false 时仍可管理文档与配置。
     * 新建默认 true；更新时若请求未传该字段则由服务端保留库内原值。
     */
    private Boolean enabled;
    /** 知识库创建时间 */
    private Date createTime;
    /** 知识库更新时间 */
    private Date updateTime;
}
