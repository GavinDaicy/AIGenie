package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.document.model.Document;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/8
 */
@Mapper
public interface DocumentMapper {
    List<Document> getAllDocumentList();

    List<Document> getDocumentListByKlCode(String knowledgeCode);

    Document getDocumentById(String documentId);

    Document getDocumentByName(String name);

    void addDocument(Document doc);

    void updateDocument(Document document);

    void deleteDocument(String documentId);

    void deleteByKnowledgeCode(String knowledgeCode);
}
