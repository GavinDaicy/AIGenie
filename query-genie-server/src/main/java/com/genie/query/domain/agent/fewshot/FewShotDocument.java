package com.genie.query.domain.agent.fewshot;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

/**
 * FewShot Q→SQL 对的领域模型。
 *
 * <p>Jackson 注解用于与存储层（Elasticsearch）的 JSON 序列化映射，
 * 与 {@link com.genie.query.domain.agent.citation.CitationItem} 的处理方式一致。
 *
 * @author daicy
 * @date 2026/4/12
 */
@Data
public class FewShotDocument {

    private String id;

    private String question;

    private String sql;

    @JsonProperty("datasource_id")
    private String datasourceId;

    @JsonProperty("question_vector")
    private float[] questionVector;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date createdAt;

    @JsonProperty("feedback_id")
    private String feedbackId;
}
