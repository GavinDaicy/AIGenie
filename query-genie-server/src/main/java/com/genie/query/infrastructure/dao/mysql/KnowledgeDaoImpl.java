package com.genie.query.infrastructure.dao.mysql;

import com.genie.query.domain.knowledge.dao.KnowledgeDAO;
import com.genie.query.domain.knowledge.model.KLField;
import com.genie.query.domain.knowledge.model.Knowledge;
import com.genie.query.infrastructure.dao.mysql.mapper.KLFieldMapper;
import com.genie.query.infrastructure.dao.mysql.mapper.KnowledgeMapper;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/8
 */
@Repository
public class KnowledgeDaoImpl implements KnowledgeDAO {

    @Autowired
    private KnowledgeMapper knowledgeMapper;
    @Autowired
    private KLFieldMapper klFieldMapper;

    @Override
    public List<Knowledge> queryKnowledgeList() {
        List<Knowledge> knowledgeList = knowledgeMapper.getKnowledgeList();
        List<String> knowledgeIdList = knowledgeList.stream().map(Knowledge::getId).toList();
        if (!CollectionUtils.isEmpty(knowledgeIdList)) {
            List<KLField> klFieldList = klFieldMapper.getKLFieldList(knowledgeIdList);
            knowledgeList.forEach(knowledge -> knowledge.setFields(klFieldList.stream().filter(klField -> klField.getKnowledgeId().equals(knowledge.getId())).toList()));
        }
        return knowledgeList;
    }

    @Override
    @Transactional
    public void addKnowledge(Knowledge knowledge) {
        knowledgeMapper.addKnowledge(knowledge);
        knowledge.getFields().forEach(klField -> {
            klField.setId(SnowflakeIdUtils.getNextStringId());
            klField.setKnowledgeId(knowledge.getId());
        });
        klFieldMapper.addKLFieldList(knowledge.getFields());
    }

    @Override
    public Knowledge getKnowledgeByCode(String code) {
        Knowledge knowledge = knowledgeMapper.getKnowledgeByCode(code);
        if (knowledge == null) {
            return knowledge;
        }
        knowledge.setFields(klFieldMapper.getKLFieldList(List.of(knowledge.getId())));
        return knowledge;
    }

    @Override
    public Knowledge getKnowledgeByName(String name) {
        Knowledge knowledge = knowledgeMapper.getKnowledgeByName(name);
        if (knowledge == null) {
            return knowledge;
        }
        knowledge.setFields(klFieldMapper.getKLFieldList(List.of(knowledge.getId())));
        return knowledge;
    }

    @Override
    public void updateKnowledgeBaseInfo(Knowledge knowledge) {
        knowledgeMapper.updateKnowledgeBaseInfo(knowledge);
    }

    @Override
    @Transactional
    public void deleteKnowledge(String code) {
        Knowledge knowledge = knowledgeMapper.getKnowledgeByCode(code);
        if (knowledge == null) {
            return;
        }
        knowledgeMapper.deleteKnowledge(code);
        klFieldMapper.deleteKLFieldList(List.of(knowledge.getId()));
    }
}
