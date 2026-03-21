package com.genie.query.application.event.listener;

import com.genie.query.application.event.DocParsedEvent;
import com.genie.query.application.event.DocUploadedEvent;
import com.genie.query.domain.document.model.DocStatus;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.service.DocumentService;
import com.genie.query.domain.fileparser.DocumentParserFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

/**
 * 文件上传完成后，触发事件进行文件解析。
 * 使用事务提交后异步消费，不阻塞接口返回，不影响主事务。
 *
 * @author daicy
 * @date 2026/1/7
 */
@Slf4j
@Component
public class UploadedListener {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private DocumentService documentService;

    @Async("documentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocUploaded(DocUploadedEvent event) {
        log.info("文件上传完成，开始解析文件。docId: {}", event.getDocumentId());
        // 修改文档状态到解析中
        documentService.changeStatus(event.getDocumentId(), DocStatus.PARSING);
        // 开始解析
        Document document = documentService.getDocumentById(event.getDocumentId());
        // 转换解析后文档
        try {
            DocumentParserFactory.getParser(document.getType()).parse(document);
        } catch (IOException e) {
            log.error("文件解析失败", e);
            documentService.changeStatus(event.getDocumentId(), DocStatus.FAILED);
            throw new RuntimeException(e);
        }
        // 解析完成事件：需要切块、向量化、存储向量化后数据（携带 knowledgeCode 供按知识库字段配置写入 ES）
        eventPublisher.publishEvent(new DocParsedEvent(this, event.getDocumentId(), event.getKnowledgeCode()));
    }
}
