package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.document.dao.DocumentDAO;
import com.genie.query.domain.document.model.Document;
import com.genie.query.infrastructure.dao.mysql.mapper.DocumentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/8
 */
@Repository
public class DocumentDaoImpl implements DocumentDAO {

    @Autowired
    private DocumentMapper documentMapper;

    @Override
    public List<Document> getAllDocumentList() {
        return documentMapper.getAllDocumentList();
    }

    @Override
    public List<Document> getDocumentList(String knowledgeCode) {
        return documentMapper.getDocumentListByKlCode(knowledgeCode);
    }

    @Override
    public Document getDocumentById(String documentId) {
        return documentMapper.getDocumentById(documentId);
    }

    @Override
    public boolean existName(String name) {
        return documentMapper.getDocumentByName(name) != null;
    }

    @Override
    public void addDocument(Document doc) {
        documentMapper.addDocument(doc);
    }

    @Override
    public void updateDocument(Document document) {
        documentMapper.updateDocument(document);
    }

    @Override
    @Transactional
    public void deleteDocument(String documentId) {
        documentMapper.deleteDocument(documentId);
    }

    @Override
    public void deleteDocByKlCode(String knowledgeCode) {
        documentMapper.deleteByKnowledgeCode(knowledgeCode);
    }
}
