package com.genie.query.domain.query.service;

/**
 * Query 改写 Prompt 领域策略。
 */
public interface QueryRewritePromptStrategy {
    String buildSystemPrompt(int expandedCount);

    String buildUserPrompt(String originalQuestion, QueryRewriteService.QueryRewriteContext context);
}
