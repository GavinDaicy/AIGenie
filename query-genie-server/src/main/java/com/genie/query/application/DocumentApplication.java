package com.genie.query.application;

import com.genie.query.application.event.DocUploadedEvent;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.model.dto.RemoteDocumentDTO;
import com.genie.query.domain.document.model.dto.UploadDocumentDTO;
import com.genie.query.domain.document.service.DocumentService;
import com.genie.query.domain.fileparser.web.WEBLinkParserFactory;
import com.genie.query.domain.knowledge.service.KnowledgeService;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.domain.vectorstore.VectorStore;
import com.genie.query.infrastructure.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Service
public class DocumentApplication {

    @Autowired
    private DocumentService documentService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ObjectStore objectStore;
    @Autowired
    private VectorStore vectorStore;

    public List<Document> queryDocumentList(String knowledgeCode) {
        if (StringUtils.isBlank(knowledgeCode)) {
            return documentService.getAllDocumentList();
        }
        if (!knowledgeService.existKnowledge(knowledgeCode)) {
            throw new BusinessException("知识库不存在");
        }
        return documentService.getDocumentList(knowledgeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Document addLocalDocument(UploadDocumentDTO document) {
        if (StringUtils.isBlank(document.getKnowledgeCode())) {
            throw new BusinessException("上传文档必须指定目标知识库编码");
        }
        if (!knowledgeService.existKnowledge(document.getKnowledgeCode())) {
            throw new BusinessException("知识库不存在");
        }
        Document doc = documentService.addLocalDocument(document);
        // 上传文件到对象存储
        objectStore.uploadFile(doc.getObjectStorePathOrigin(), document.getFile());
        // 发布上传完成事件，事件响应触发解析（携带 knowledgeCode 供切块、向量化使用）
        eventPublisher.publishEvent(new DocUploadedEvent(this, doc.getId(), document.getKnowledgeCode()));
        return doc;
    }

    @Transactional(rollbackFor = Exception.class)
    public Document addRemoteDocument(RemoteDocumentDTO document) throws IOException, InterruptedException {
        if (StringUtils.isBlank(document.getKnowledgeCode())) {
            throw new BusinessException("添加远程文档必须指定目标知识库编码");
        }
        if (!knowledgeService.existKnowledge(document.getKnowledgeCode())) {
            throw new BusinessException("知识库不存在");
        }
        Document doc = documentService.addRemoteDocument(document);
        // 解析并上传链接文件到对象存储
        String originValue = WEBLinkParserFactory.getParser(doc.getType()).parse(doc.getOriginUri());
        objectStore.saveFile(doc.getObjectStorePathOrigin(), originValue);
        eventPublisher.publishEvent(new DocUploadedEvent(this, doc.getId(), document.getKnowledgeCode()));
        return doc;
    }

    public Document updateDocument(Document document) {
        if (StringUtils.isBlank(document.getName())) {
            throw new BusinessException("文件名不能为空");
        }
        documentService.updateDocument(document);
        return document;
    }

    @Transactional
    public void deleteDocument(String documentId) throws IOException {
        documentService.deleteDocument(documentId);
    }

    /**
     * 根据文档 id 查询该文档在向量库中的全部分块数据（不含向量字段）。
     */
    public List<Map<String, Object>> listChunksByDocumentId(String documentId) throws IOException {
        Document doc = documentService.getDocumentById(documentId);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        if (StringUtils.isBlank(doc.getKnowledgeCode())) {
            return Collections.emptyList();
        }
        return vectorStore.listChunksByDocId(doc.getKnowledgeCode(), documentId);
    }
}
