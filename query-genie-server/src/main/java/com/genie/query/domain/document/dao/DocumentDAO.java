package com.genie.query.domain.document.dao;

import com.genie.query.domain.document.model.Document;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
public interface DocumentDAO {

    List<Document> getAllDocumentList();

    List<Document> getDocumentList(String knowledgeCode);

    Document getDocumentById(String documentId);

    boolean existName(String name);

    void addDocument(Document doc);

    void updateDocument(Document document);

    void deleteDocument(String documentId);

    void deleteDocByKlCode(String knowledgeCode);

}
