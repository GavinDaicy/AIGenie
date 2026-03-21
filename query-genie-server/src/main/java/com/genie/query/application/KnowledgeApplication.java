package com.genie.query.application;

import com.genie.query.domain.document.service.DocumentService;
import com.genie.query.domain.knowledge.model.Knowledge;
import com.genie.query.domain.knowledge.service.KnowledgeService;
import com.genie.query.infrastructure.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Service
public class KnowledgeApplication {

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private DocumentService documentService;

    public List<Knowledge> queryKnowledgeList() {
        return knowledgeService.queryKnowledgeList();
    }

    public Knowledge createKnowledge(Knowledge knowledge) {
        checkKnowledgeBaseInfo(knowledge);
        // 编码统一小写，方便与ES集成
        knowledge.setCode(StringUtils.lowerCase(knowledge.getCode()));
        knowledgeService.createKnowledge(knowledge);
        return knowledge;
    }

    public Knowledge updateKnowledgeBaseInfo(Knowledge knowledge) {
        checkKnowledgeBaseInfo(knowledge);
        knowledgeService.updateKnowledgeBaseInfo(knowledge);
        return knowledge;
    }

    @Transactional
    public void deleteKnowledge(String code) {
        documentService.deleteDocByKlCode(code);
        knowledgeService.deleteKnowledge(code);
    }

    private static void checkKnowledgeBaseInfo(Knowledge knowledge) {
        if (StringUtils.isBlank(knowledge.getCode()) || StringUtils.isBlank(knowledge.getName())) {
            throw new BusinessException("知识库编码和名称不能为空");
        }
        // 编码必须是字母和数字
        if (!StringUtils.isAlphanumeric(knowledge.getCode())) {
            throw new BusinessException("知识库编码只能是字母和数字");
        }
    }

    public void publishKnowledge(String code) {
        knowledgeService.publishKnowledge(code);
    }
}
