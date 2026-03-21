package com.genie.query.domain.knowledge.dao;

import com.genie.query.domain.knowledge.model.Knowledge;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
public interface KnowledgeDAO {
    List<Knowledge> queryKnowledgeList();

    void addKnowledge(Knowledge knowledge);

    Knowledge getKnowledgeByCode(String code);

    Knowledge getKnowledgeByName(String name);

    void updateKnowledgeBaseInfo(Knowledge knowledge);

    void deleteKnowledge(String code);
}
