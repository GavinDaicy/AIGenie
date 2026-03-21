package com.genie.query.domain.qa.service;

import com.genie.query.domain.query.model.QueryResultEntry;

import java.util.List;

/**
 * 问答 prompt 领域策略：负责系统提示词与上下文拼装规则。
 */
public interface AnswerPromptStrategy {
    String systemPrompt();

    String emptyContextAnswer();

    String buildUserPrompt(String question, List<QueryResultEntry> contextEntries);
}
