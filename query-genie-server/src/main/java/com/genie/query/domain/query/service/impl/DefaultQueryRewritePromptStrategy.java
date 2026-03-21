package com.genie.query.domain.query.service.impl;

import com.genie.query.domain.query.service.QueryRewritePromptStrategy;
import com.genie.query.domain.query.service.QueryRewriteService;
import org.springframework.stereotype.Service;

/**
 * 默认 Query 改写 Prompt 策略。
 */
@Service
public class DefaultQueryRewritePromptStrategy implements QueryRewritePromptStrategy {
    @Override
    public String buildSystemPrompt(int expandedCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个帮助生成检索查询的助手。")
                .append("给定用户的自然语言问题，你需要：")
                .append("1. 先对原问题进行纠错、指代消解、术语归一化，生成一个清晰、完整且适合检索的主要查询（main_query）；")
                .append("2. 再基于相同信息需求，生成若干扩展查询（expanded_queries），")
                .append("这些扩展查询可以是同义表述、不同角度的问法，或在不改变整体意图的前提下适度拆分子问题。")
                .append("请保持与原问题相同的语言（例如中文问题就返回中文查询）。")
                .append("返回一个严格的 JSON，对象结构如下：")
                .append("{\"main_query\": \"...\", \"expanded_queries\": [\"...\", \"...\"]}。");
        if (expandedCount <= 0) {
            sb.append("当前只需要 main_query，expanded_queries 可以为空数组。");
        } else {
            sb.append("expanded_queries 的期望数量为 ").append(expandedCount)
                    .append("，若难以满足可以少于该数量，但不要多于该数量。");
        }
        return sb.toString();
    }

    @Override
    public String buildUserPrompt(String originalQuestion, QueryRewriteService.QueryRewriteContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户原始问题：").append(originalQuestion.trim()).append("\n");
        if (context != null && context.getKnowledgeCodes() != null && !context.getKnowledgeCodes().isEmpty()) {
            sb.append("可用的知识库编码列表：");
            sb.append(String.join(",", context.getKnowledgeCodes()));
            sb.append("\n");
        }
        sb.append("请按照系统提示生成 JSON。");
        return sb.toString();
    }
}
