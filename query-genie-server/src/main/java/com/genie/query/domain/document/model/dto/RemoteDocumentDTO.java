package com.genie.query.domain.document.model.dto;

import com.genie.query.domain.document.model.DocCategory;
import com.genie.query.domain.document.model.DocType;
import lombok.Data;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Data
public class RemoteDocumentDTO {
    /** 目标知识库编码，上传时必填，文档将直接归属该知识库 */
    private String knowledgeCode;
    private String name;
    private String description;
    private DocType docType;
    private DocCategory category;
    private String url;
}
