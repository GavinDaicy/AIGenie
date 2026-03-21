package com.genie.query.application.event.listener;

import com.genie.query.application.event.DocParsedEvent;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.model.DocStatus;
import com.genie.query.domain.document.service.DocumentService;
import com.genie.query.domain.etlpipeline.index.DocumentIndex;
import com.genie.query.domain.etlpipeline.model.Chunk;
import com.genie.query.domain.etlpipeline.spliter.DocumentSplitter;
import com.genie.query.domain.etlpipeline.spliter.DocumentSplitterFactory;
import com.genie.query.domain.knowledge.model.KLField;
import com.genie.query.domain.knowledge.model.Knowledge;
import com.genie.query.domain.knowledge.service.KnowledgeService;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.domain.vectorstore.VectorStore;
import com.genie.query.infrastructure.vectorstore.es.KLFieldMappingBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档解析完成后的入口，统一编排全流程：
 * 1. spliter 切块 → 2. index 向量化并转为通用 Map → 3. vectorStore 写入向量库。
 * 单内容字段且可语义检索 → 递归切块；多字段 → 按 Excel/CSV 行切块。
 * 使用异步消费，不阻塞上游事件发布。
 *
 * @author daicy
 * @date 2026/1/16
 */
@Slf4j
@Component
public class ParsedListener {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ObjectStore objectStore;
    @Autowired
    private DocumentSplitterFactory documentSplitterFactory;
    @Autowired
    private DocumentIndex documentIndex;
    @Autowired
    private VectorStore vectorStore;

    @Async("documentTaskExecutor")
    @EventListener
    public void onDocParsed(DocParsedEvent event) {
        String docId = event.getDocumentId();
        String knowledgeCode = event.getKnowledgeCode();
        log.info("文件解析完成，开始进行切块、向量化。docId: {}, knowledgeCode: {}", docId, knowledgeCode);

        Document doc = documentService.getDocumentById(docId);
        if (doc == null) {
            log.warn("文档不存在，跳过切块。docId: {}", docId);
            return;
        }
        Knowledge knowledge = knowledgeService.getKnowledgeByCode(knowledgeCode);
        if (knowledge == null || knowledge.getFields() == null) {
            log.warn("知识库不存在或未配置字段，跳过切块。knowledgeCode: {}", knowledgeCode);
            return;
        }

        List<KLField> userFields = knowledge.getFields().stream()
                .filter(f -> f != null && !"id".equals(f.getFieldKey()) && !"doc_id".equals(f.getFieldKey()))
                .collect(Collectors.toList());
        if (userFields.isEmpty()) {
            log.warn("知识库无用户配置字段，跳过切块。knowledgeCode: {}", knowledgeCode);
            return;
        }

        try (InputStream parsedInput = objectStore.downloadFile(doc.getObjectStorePathParsed())) {
            DocumentSplitter splitter = documentSplitterFactory.getSplitter(doc, knowledge, userFields);
            List<Chunk> chunks = splitter.split(doc, parsedInput, userFields);
            if (chunks.isEmpty()) {
                log.info("切块结果为空。docId: {}", docId);
                return;
            }

            List<Map<String, Object>> documents = documentIndex.toIndexedDocuments(knowledgeCode, docId, chunks, userFields);
            if (doc.getUpdateTime() != null && documents != null && !documents.isEmpty()) {
                for (Map<String, Object> d : documents) {
                    if (d != null) {
                        d.put(KLFieldMappingBuilder.DOC_UPDATE_TIME_FIELD, doc.getUpdateTime());
                    }
                }
            }
            if (!documents.isEmpty()) {
                try {
                    vectorStore.deleteByDocId(knowledgeCode, docId);
                    vectorStore.batchAddDocuments(knowledgeCode, docId, documents);
                } catch (Exception e) {
                    log.error("向量库写入失败。knowledgeCode={}, docId={}", knowledgeCode, docId, e);
                    documentService.changeStatus(docId, DocStatus.FAILED);
                    return;
                }
            }
            documentService.changeStatus(docId, DocStatus.PARSED);
        } catch (Exception e) {
            log.error("切块或向量化失败。docId: {}, knowledgeCode: {}", docId, knowledgeCode, e);
            documentService.changeStatus(docId, DocStatus.FAILED);
        }
    }

}
