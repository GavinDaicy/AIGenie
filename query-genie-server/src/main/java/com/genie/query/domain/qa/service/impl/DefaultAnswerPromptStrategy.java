package com.genie.query.domain.qa.service.impl;

import com.genie.query.domain.qa.config.QaPolicyProperties;
import com.genie.query.domain.qa.service.AnswerPromptStrategy;
import com.genie.query.domain.query.model.QueryResultEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认问答 prompt 策略。
 */
@Service
public class DefaultAnswerPromptStrategy implements AnswerPromptStrategy {
    private static final String SYSTEM_PROMPT = "你是一个基于知识库的问答助手。请仅根据下面提供的参考内容回答用户问题。"
            + "如果参考内容中不包含与问题相关的信息，请明确说明「根据当前知识库无法回答该问题」。"
            + "回答请简洁准确，不要编造参考内容中不存在的信息。"
            + "参考内容会按顺序编号为 1,2,3,...，请在使用到某条参考内容的句子或段落后添加对应的引用编号角标，例如「根据文档内容...[1]」。"
            + "引用编号必须严格对应参考内容的顺序，不要生成不存在的编号，也不要在未使用参考内容时随意添加编号。";

    private final QaPolicyProperties qaPolicyProperties;

    public DefaultAnswerPromptStrategy(QaPolicyProperties qaPolicyProperties) {
        this.qaPolicyProperties = qaPolicyProperties;
    }

    @Override
    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    @Override
    public String emptyContextAnswer() {
        return "未找到相关参考内容，无法基于知识库回答该问题。";
    }

    @Override
    public String buildUserPrompt(String question, List<QueryResultEntry> contextEntries) {
        String contextText = buildContext(contextEntries);
        return "【参考内容（按编号 1,2,3,... 排列，下文回答时请使用对应编号作为引用角标）】\n\n"
                + contextText
                + "\n\n【用户问题】\n\n"
                + question;
    }

    private String buildContext(List<QueryResultEntry> entries) {
        int maxChars = qaPolicyProperties.getMaxContextChars();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            QueryResultEntry e = entries.get(i);
            String block = formatEntry(e, i + 1);
            if (sb.length() + block.length() > maxChars) {
                int remaining = maxChars - sb.length();
                if (remaining > 100) {
                    sb.append(block, 0, Math.min(remaining - 50, block.length())).append("...\n");
                }
                break;
            }
            sb.append(block).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String formatEntry(QueryResultEntry e, int index) {
        String docName = e.getDocument() != null ? e.getDocument().getName() : "";
        String kb = e.getKnowledgeCode() != null ? e.getKnowledgeCode() : "";
        Map<String, Object> chunk = e.getChunkContent();
        String sectionTitle = chunk != null && chunk.get("section_title") != null
                ? String.valueOf(chunk.get("section_title"))
                : "";
        String header;
        if (!sectionTitle.isBlank()) {
            header = String.format("【来源%d】[知识库: %s] [文档: %s] [章节: %s]", index, kb, docName, sectionTitle);
        } else {
            header = String.format("【来源%d】[知识库: %s] [文档: %s]", index, kb, docName);
        }
        String body = formatChunkContent(chunk);
        return header + "\n" + body;
    }

    private String formatChunkContent(Map<String, Object> chunkContent) {
        if (chunkContent == null || chunkContent.isEmpty()) {
            return "";
        }
        return chunkContent.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }
}
