package com.genie.query.domain.qa.model;

import com.genie.query.domain.query.model.QueryResultEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能问答领域结果：大模型生成的答案及引用来源。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResult {
    /** 生成的答案正文 */
    private String answer;
    /** 引用来源（检索得到的条目，用于 application 层映射为 QaSourceItem） */
    private List<QueryResultEntry> sources;
}
