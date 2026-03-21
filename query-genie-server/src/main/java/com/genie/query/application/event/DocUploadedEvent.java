package com.genie.query.application.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Setter
@Getter
public class DocUploadedEvent extends ApplicationEvent {
    private String documentId;
    /** 文档所属知识库编码，用于后续解析、切块、向量化时按该知识库的字段配置写入 ES */
    private String knowledgeCode;

    public DocUploadedEvent(Object source, String documentId, String knowledgeCode) {
        super(source);
        this.documentId = documentId;
        this.knowledgeCode = knowledgeCode;
    }
}
