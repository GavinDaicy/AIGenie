package com.genie.query.domain.agent.citation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一引用数据项。
 *
 * <p>兼容 Qa.vue 的 sources 格式（type=KB），并扩展支持 SQL / WEB 类型。
 * <p>DB 存储：序列化为 JSON 数组，存入 chat_message.citations_json。
 * <p>前端对齐：前端统一用 CitationItem[] 描述引用列表，按 index 与答案中的 [N] 对应。
 *
 * @author daicy
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CitationItem {

    public enum CitationType { KB, SQL, WEB }

    /** 引用序号，与答案文本中 [N] 的 N 对应，从 1 开始 */
    private int index;
    /** 引用类型 */
    private CitationType type;

    // ── KB 类型（兼容 QaSourceItem / Qa.vue sources 字段） ──
    private String knowledgeCode;
    private String documentName;
    private Double score;
    /** 分块内容，key=字段名，val=字段值（与 Qa.vue 现有 chunkContent 结构一致） */
    private Map<String, Object> chunkContent;

    // ── SQL 类型（Agent 专属） ──
    private String sql;
    private List<String> columns;
    private List<List<Object>> rows;
    private Integer rowCount;
    private Long executionTimeMs;

    // ── WEB 类型（Agent 专属） ──
    private String title;
    private String url;
    private String snippet;
    private String source;
    private String publishedTime;
}
