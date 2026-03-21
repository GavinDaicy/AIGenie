package com.genie.query.domain.document.service;

import com.genie.query.domain.common.IdGenerator;
import com.genie.query.domain.common.LockKeys;
import com.genie.query.domain.common.LockManager;
import com.genie.query.domain.document.dao.DocumentDAO;
import com.genie.query.domain.document.model.DocStatus;
import com.genie.query.domain.document.model.DocType;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.document.model.dto.RemoteDocumentDTO;
import com.genie.query.domain.document.model.dto.UploadDocumentDTO;
import com.genie.query.domain.objectstore.ObjectStore;
import com.genie.query.domain.vectorstore.VectorStore;
import com.genie.query.domain.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Service
public class DocumentService {

    @Autowired
    private DocumentDAO documentDAO;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ObjectStore objectStore;
    @Autowired
    private IdGenerator idGenerator;
    @Autowired
    private LockManager lockManager;

    public List<Document> getAllDocumentList() {
        return documentDAO.getAllDocumentList();
    }

    public List<Document> getDocumentList(String knowledgeCode) {
        return documentDAO.getDocumentList(knowledgeCode);
    }

    public Document addLocalDocument(UploadDocumentDTO document) {
        Document doc = convert(document);
        doc.setStatus(DocStatus.UPLOADING);
        checkNameAndSave(doc, true);
        return doc;
    }

    public Document addRemoteDocument(RemoteDocumentDTO document) {
        Document doc = convert(document);
        doc.setStatus(DocStatus.UPLOADING);
        // 保存文件到数据库
        checkNameAndSave(doc, true);
        return doc;
    }

    public void updateDocument(Document document) {
        lockManager.withLock(LockKeys.DOCUMENT_INFO_KEY, () -> {
            if (documentDAO.existName(document.getName())) {
                throw new BusinessException("文件名重复，请重新修改名称");
            }
            document.setUpdateTime(new Date());
            documentDAO.updateDocument(document);
        });
    }

    public boolean existDocument(String documentId) {
        return documentDAO.getDocumentById(documentId) != null;
    }

    public void deleteDocument(String documentId) throws IOException {
        Document document = getDocumentById(documentId);
        documentDAO.deleteDocument(documentId);
        if (document == null) {
            return;
        }
        if (StringUtils.isBlank(document.getKnowledgeCode())) {
            return;
        }
        vectorStore.deleteByDocId(document.getKnowledgeCode(), documentId);
        CompletableFuture.runAsync(() -> objectStore.deletePath(getRootDocStorePath(documentId)));
    }

    public void deleteDocByKlCode(String knowledgeCode) {
        List<String> pathsToDelete = new ArrayList<>();
        lockManager.withLock(LockKeys.DOCUMENT_KNOWLEDGE_MAPPING_KEY, () -> {
            List<Document> documents = getDocumentList(knowledgeCode);
            documentDAO.deleteDocByKlCode(knowledgeCode);
            pathsToDelete.addAll(documents.stream()
                    .map(doc -> getRootDocStorePath(doc.getId()))
                    .collect(Collectors.toList()));
        });
        if (!pathsToDelete.isEmpty()) {
            CompletableFuture.runAsync(() ->
                    pathsToDelete.forEach(objectStore::deletePath));
        }
    }

    public Document getDocumentById(String documentId) {
        return documentDAO.getDocumentById(documentId);
    }

    public void changeStatus(String documentId, DocStatus status) {
        Document document = getDocumentById(documentId);
        document.setStatus(status);
        document.setUpdateTime(new Date());
        documentDAO.updateDocument(document);
    }

    private void checkNameAndSave(Document doc, boolean renameAble) {
        lockManager.withLock(LockKeys.DOCUMENT_INFO_KEY, () -> {
            if (renameAble) {
                String newName = getNewName(doc.getName());
                doc.setName(newName);
                doc.setObjectStorePathOrigin(genOriginObjectStorePath(doc));
                doc.setObjectStorePathParsed(genParsedObjectStorePath(doc));
            }
            if (documentDAO.existName(doc.getName())) {
                throw new BusinessException("文件名重复，请重新修改名称");
            }
            // 保存文件到数据库
            documentDAO.addDocument(doc);
        });
    }

    private String getNewName(String name) {
        // 获取文件名，刨掉后缀
        String nameWithoutSuffix = StringUtils.substringBeforeLast(name, ".");
        String suffix = StringUtils.substringAfterLast(name, ".");
        if (documentDAO.existName(name)) {
            // name最后如果以(%d)结尾，则数字+1，否则添加(1)
            String regex = ".*\\((\\d+)\\)$";
            if (nameWithoutSuffix.matches(regex)) {
                int beginIndex = nameWithoutSuffix.lastIndexOf("(");
                int endIndex = nameWithoutSuffix.lastIndexOf(")");
                String strNum = nameWithoutSuffix.substring(beginIndex + 1, endIndex);
                if (NumberUtils.isDigits(strNum)) {
                    int num = Integer.parseInt(strNum);
                    nameWithoutSuffix = nameWithoutSuffix.replaceAll("\\(\\d+\\)$", "(" + (num + 1) + ")");
                    name = StringUtils.isBlank(suffix) ? nameWithoutSuffix : nameWithoutSuffix + "." + suffix;
                    return getNewName(name);
                }
            }
            name = StringUtils.isBlank(suffix) ? nameWithoutSuffix + "(1)" : nameWithoutSuffix + "(1)." + suffix;
            return getNewName(name);
        } else {
            return name;
        }
    }

    private Document convert(UploadDocumentDTO document) {
        Document doc = new Document();
        doc.setId(idGenerator.nextId());
        doc.setName(document.getName());
        doc.setDescription(document.getDescription());
        doc.setCategory(document.getCategory());
        doc.setStatus(DocStatus.UPLOADING);
        doc.setKnowledgeCode(document.getKnowledgeCode());
        
        doc.setType(DocType.getDocTypeByFileName(document.getName()));
        doc.setObjectStorePathOrigin(genOriginObjectStorePath(doc));
        doc.setObjectStorePathParsed(genParsedObjectStorePath(doc));

        doc.setCreateTime(new Date());
        doc.setUpdateTime(new Date());
        return doc;
    }

    private Document convert(RemoteDocumentDTO document) {
        Document doc = new Document();
        doc.setId(idGenerator.nextId());
        doc.setName(document.getName());
        doc.setDescription(document.getDescription());
        doc.setCategory(document.getCategory());
        doc.setType(document.getDocType());
        doc.setStatus(DocStatus.TOPARSE);
        doc.setOriginUri(document.getUrl());
        doc.setKnowledgeCode(document.getKnowledgeCode());

        doc.setObjectStorePathOrigin(genOriginObjectStorePath(doc));
        doc.setObjectStorePathParsed(genParsedObjectStorePath(doc));

        doc.setCreateTime(new Date());
        doc.setUpdateTime(new Date());
        return doc;
    }

    public String getRootDocStorePath(String id) {
        return "/doc/" + id;
    }

    private String genParsedObjectStorePath(Document doc) {
        return "/doc/" + doc.getId() + "/parsed/" + doc.getName();
    }

    private String genOriginObjectStorePath(Document doc) {
        return "/doc/" + doc.getId() + "/origin/" + doc.getName();
    }
}
