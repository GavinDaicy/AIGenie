package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.knowledge.model.Knowledge;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/8
 */
@Mapper
public interface KnowledgeMapper {
    List<Knowledge> getKnowledgeList();

    void addKnowledge(Knowledge knowledge);

    Knowledge getKnowledgeByCode(String code);

    Knowledge getKnowledgeByName(String name);

    void updateKnowledgeBaseInfo(Knowledge knowledge);

    void deleteKnowledge(String code);
}
