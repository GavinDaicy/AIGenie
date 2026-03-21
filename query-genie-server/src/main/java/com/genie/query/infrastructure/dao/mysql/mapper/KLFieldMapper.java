package com.genie.query.infrastructure.dao.mysql.mapper;

import com.genie.query.domain.knowledge.model.KLField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/8
 */
@Mapper
public interface KLFieldMapper {
    List<KLField> getKLFieldList(@Param("knowledgeIdList") List<String> knowledgeIdList);

    void addKLFieldList(@Param("list") List<KLField> fields);

    void deleteKLFieldList(@Param("list") List<String> id);
}
