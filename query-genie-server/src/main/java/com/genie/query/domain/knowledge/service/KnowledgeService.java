package com.genie.query.domain.knowledge.service;

import com.genie.query.domain.common.IdGenerator;
import com.genie.query.domain.common.LockKeys;
import com.genie.query.domain.common.LockManager;
import com.genie.query.domain.exception.BusinessException;
import com.genie.query.domain.knowledge.dao.KnowledgeDAO;
import com.genie.query.domain.knowledge.model.ChunkingPolicy;
import com.genie.query.domain.knowledge.model.ChunkingStrategy;
import com.genie.query.domain.knowledge.model.KLField;
import com.genie.query.domain.knowledge.model.KLFieldType;
import com.genie.query.domain.knowledge.model.KLState;
import com.genie.query.domain.knowledge.model.Knowledge;
import com.genie.query.domain.knowledge.model.TimeDecayConfig;
import com.genie.query.domain.knowledge.model.TimeDecayFieldSource;
import com.genie.query.domain.vectorstore.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Slf4j
@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeDAO knowledgeDAO;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private IdGenerator idGenerator;
    @Autowired
    private LockManager lockManager;

    public List<Knowledge> queryKnowledgeList() {
        return knowledgeDAO.queryKnowledgeList();
    }

    public void createKnowledge(Knowledge knowledge) {
        knowledge.setId(idGenerator.nextId());
        knowledge.setStatus(KLState.UNPUBLISHED);
        knowledge.setCreateTime(new Date());
        knowledge.setUpdateTime(new Date());
        // 若未显式配置分块策略，则填充默认配置
        if (knowledge.getChunkingPolicy() == null) {
            knowledge.setChunkingPolicy(defaultChunkingPolicy());
        }
        // 若未显式配置时间衰减，则默认不启用
        if (knowledge.getTimeDecayConfig() == null) {
            TimeDecayConfig td = new TimeDecayConfig();
            td.setEnabled(false);
            knowledge.setTimeDecayConfig(td);
        }
        if (knowledge.getEnabled() == null) {
            knowledge.setEnabled(true);
        }
        lockManager.withLock(LockKeys.KNOWLEDGE_INFO_KEY, () -> {
            // 查询是否已存在相同编码，相同名称的知识库
            queryRepeatKnowledge(knowledge);
            // 完善知识库固有字段
            completeKnowledgeFixedFields(knowledge);
            validateTimeDecayConfig(knowledge);
            // 添加知识库
            knowledgeDAO.addKnowledge(knowledge);
            try {
                // 创建向量库
                vectorStore.createVectorDB(knowledge.getCode(), knowledge.getFields());
            } catch (Exception e) {
                knowledgeDAO.deleteKnowledge(knowledge.getCode());
                log.error("创建向量库失败", e);
                throw new BusinessException("创建向量库失败");
            }
        });
    }

    private void completeKnowledgeFixedFields(Knowledge knowledge) {
        List<KLField> klFieldList = knowledge.getFields();
        // 固定字段 - id
        KLField idField = new KLField();
        idField.setId(idGenerator.nextId());
        idField.setKnowledgeId(knowledge.getId());
        idField.setFieldKey("id");
        idField.setName("id");
        idField.setDescription("id字段");
        idField.setType(KLFieldType.STRING);
        idField.setMatchable(true);
        idField.setFullTextSearchable(false);
        idField.setSemanticSearchable(false);
        idField.setSortable(true);
        idField.setOrd(99);
        // 固定字段 - 源文档id
        KLField docId = new KLField();
        docId.setId(idGenerator.nextId());
        docId.setKnowledgeId(knowledge.getId());
        docId.setFieldKey("doc_id");
        docId.setName("源文档id");
        docId.setDescription("源文档id字段");
        docId.setType(KLFieldType.STRING);
        docId.setMatchable(true);
        docId.setFullTextSearchable(false);
        docId.setSemanticSearchable(false);
        docId.setSortable(false);
        docId.setOrd(100);
        // 固定字段 - 聚合内容字段（用于全文与向量检索）
        KLField sysContent = new KLField();
        sysContent.setId(idGenerator.nextId());
        sysContent.setKnowledgeId(knowledge.getId());
        sysContent.setFieldKey("sys_content");
        sysContent.setName("sys_content");
        sysContent.setDescription("内置全文内容字段（系统字段，不可删除/修改）");
        sysContent.setType(KLFieldType.STRING);
        sysContent.setMatchable(false);
        sysContent.setFullTextSearchable(true);
        sysContent.setSemanticSearchable(true);
        sysContent.setSortable(false);
        sysContent.setOrd(101);
        klFieldList.add(idField);
        klFieldList.add(docId);
        klFieldList.add(sysContent);
    }

    public void updateKnowledgeBaseInfo(Knowledge knowledge) {
        knowledge.setUpdateTime(new Date());
        knowledge.setStatus(
                knowledge.getStatus() == KLState.UNPUBLISHED ?
                        KLState.UNPUBLISHED :
                        KLState.PUBLISHED_MODIFYING);
        // 更新时若前端未显式传入分块策略，保持已有配置或回退默认
        if (knowledge.getChunkingPolicy() == null) {
            Knowledge exist = knowledgeDAO.getKnowledgeByCode(knowledge.getCode());
            if (exist != null && exist.getChunkingPolicy() != null) {
                knowledge.setChunkingPolicy(exist.getChunkingPolicy());
            } else {
                knowledge.setChunkingPolicy(defaultChunkingPolicy());
            }
        }
        // 更新时间衰减 / 启用标志：若未传则保持已有（一次查询）
        if (knowledge.getTimeDecayConfig() == null || knowledge.getEnabled() == null) {
            Knowledge exist = knowledgeDAO.getKnowledgeByCode(knowledge.getCode());
            if (knowledge.getTimeDecayConfig() == null && exist != null) {
                knowledge.setTimeDecayConfig(exist.getTimeDecayConfig());
            }
            if (knowledge.getEnabled() == null) {
                knowledge.setEnabled(exist != null && exist.getEnabled() != null ? exist.getEnabled() : true);
            }
        }

        lockManager.withLock(LockKeys.KNOWLEDGE_INFO_KEY, () -> {
            queryRepeatNameKnowledge(knowledge);
            validateTimeDecayConfig(knowledge);
            knowledgeDAO.updateKnowledgeBaseInfo(knowledge);
        });
    }

    private void validateTimeDecayConfig(Knowledge knowledge) {
        if (knowledge == null || knowledge.getTimeDecayConfig() == null) {
            return;
        }
        TimeDecayConfig cfg = knowledge.getTimeDecayConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) {
            return;
        }
        if (cfg.getFieldSource() == null) {
            throw new BusinessException("时间衰减已启用，但未配置时间字段来源");
        }
        if (cfg.getFieldSource() == TimeDecayFieldSource.BUSINESS_FIELD) {
            if (cfg.getFieldKey() == null || cfg.getFieldKey().isBlank()) {
                throw new BusinessException("时间衰减已启用，请选择业务日期字段");
            }
            if (knowledge.getFields() == null || knowledge.getFields().isEmpty()) {
                throw new BusinessException("时间衰减已启用，但知识库字段为空");
            }
            boolean ok = knowledge.getFields().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(f -> cfg.getFieldKey().equals(f.getFieldKey()) && f.getType() == KLFieldType.DATE);
            if (!ok) {
                throw new BusinessException("时间衰减字段必须为 DATE 类型字段: " + cfg.getFieldKey());
            }
        }
        if (cfg.getFloor() != null && (cfg.getFloor() < 0d || cfg.getFloor() > 1d)) {
            throw new BusinessException("时间衰减 floor 必须在 0~1 之间");
        }
        if (cfg.getDecay() != null && (cfg.getDecay() <= 0d || cfg.getDecay() > 1d)) {
            throw new BusinessException("时间衰减 decay 必须在 (0,1] 之间");
        }
    }

    private void queryRepeatKnowledge(Knowledge knowledge) {
        queryRepeatCodeKnowledge(knowledge);
        queryRepeatNameKnowledge(knowledge);
    }

    public void deleteKnowledge(String code) {
        if (knowledgeDAO.getKnowledgeByCode(code) == null) {
            throw new BusinessException("知识库不存在");
        }
        try {
            vectorStore.deleteVectorDB(code);
        } catch (IOException e) {
            log.error("删除知识库失败-vectorDB", e);
            throw new BusinessException("删除知识库失败");
        }
        knowledgeDAO.deleteKnowledge(code);
    }

    public void publishKnowledge(String code) {
        if (knowledgeDAO.getKnowledgeByCode(code) == null) {
            throw new BusinessException("知识库不存在");
        }
        Knowledge knowledge = knowledgeDAO.getKnowledgeByCode(code);
        knowledge.setStatus(KLState.PUBLISHED);
        knowledgeDAO.updateKnowledgeBaseInfo(knowledge);
    }

    public Boolean existKnowledge(String code) {
        return knowledgeDAO.getKnowledgeByCode(code) != null;
    }

    public Knowledge getKnowledgeByCode(String code) {
        return knowledgeDAO.getKnowledgeByCode(code);
    }

    private void queryRepeatCodeKnowledge(Knowledge knowledge) {
        if (knowledgeDAO.getKnowledgeByCode(knowledge.getCode()) != null) {
            throw new BusinessException("知识库编码已存在，请修改后再试");
        }
    }
    private void queryRepeatNameKnowledge(Knowledge knowledge) {
        Knowledge repeatKnowledge = knowledgeDAO.getKnowledgeByName(knowledge.getName());
        if (repeatKnowledge != null && !repeatKnowledge.getId().equals(knowledge.getId())) {
            throw new BusinessException("知识库名称已存在，请修改后再试");
        }
    }

    /**
     * 默认分块策略：长文本递归切块，具体大小与重叠沿用全局配置（由切块实现读取）。
     */
    private ChunkingPolicy defaultChunkingPolicy() {
        ChunkingPolicy policy = new ChunkingPolicy();
        policy.setStrategy(ChunkingStrategy.RECURSIVE_TEXT);
        return policy;
    }
}
