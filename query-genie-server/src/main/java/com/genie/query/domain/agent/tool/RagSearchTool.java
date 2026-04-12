package com.genie.query.domain.agent.tool;

import com.genie.query.domain.agent.citation.CitationItem;
import com.genie.query.domain.agent.citation.CitationRegistry;
import com.genie.query.domain.query.model.QueryResultEntry;
import com.genie.query.domain.qa.service.QaQueryService;
import com.genie.query.domain.query.service.QueryRewriteService;
import com.genie.query.domain.vectorstore.SearchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.genie.query.domain.agent.tool.spi.AgentTool;
import com.genie.query.domain.agent.tool.spi.AgentToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 知识库检索工具：封装现有 {@link QaQueryService}，供 Spring AI Agent 通过 Function Calling 调用。
 *
 * <p>工具触发场景：产品信息、规范文件、操作手册等知识文档类问题。
 * 工具执行后返回格式化的检索片段文本（含来源标注），供 LLM 生成最终答案时引用。
 *
 * @author daicy
 * @date 2026/4/2
 */
@AgentToolMeta(name = "knowledge", group = "knowledge", forceable = true, toolForceField = "knowledge")
@Component
public class RagSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RagSearchTool.class);

    private static final int DEFAULT_SEARCH_SIZE = 10;
    private static final int DEFAULT_RERANK_FACTOR = 2;
    private static final int MAX_RESULT_CHARS = 4000;

    @Autowired
    private QaQueryService qaQueryService;

    @Autowired(required = false)
    private QueryRewriteService queryRewriteService;

    /**
     * 搜索内部知识库，返回相关文档片段。
     * 适用于产品信息、规范文件、操作手册等知识文档类问题。
     *
     * @param question       用户的自然语言问题
     * @param knowledgeCodes 知识库编码列表，多个用英文逗号分隔（为空则搜索全部知识库）
     * @return 相关文档片段文本（含来源标注）；未检索到时返回说明信息
     */
    @Tool(description = "搜索内部知识库，返回相关文档片段。适用于产品信息、规范文件、操作手册等知识文档类问题。")
    public String searchKnowledge(
            @ToolParam(description = "用户的自然语言问题") String question,
            @ToolParam(description = "知识库编码列表，多个用英文逗号分隔；为空则搜索全部知识库") String knowledgeCodes) {

        List<String> kbCodes = parseKnowledgeCodes(knowledgeCodes);
        log.info("[RagSearchTool] 开始检索 | question={} | knowledgeCodes={}", question, kbCodes);

        try {
            List<String> queries = resolveQueries(question, kbCodes);
            List<QueryResultEntry> results = qaQueryService.searchWithQueries(
                    queries,
                    kbCodes.isEmpty() ? null : kbCodes,
                    SearchMode.HYBRID,
                    DEFAULT_SEARCH_SIZE,
                    DEFAULT_SEARCH_SIZE * DEFAULT_RERANK_FACTOR,
                    true,
                    null
            );

            if (results == null || results.isEmpty()) {
                log.info("[RagSearchTool] 未检索到相关内容");
                return "知识库中未找到与该问题相关的内容。";
            }

            log.info("[RagSearchTool] 检索到 {} 条结果", results.size());
            for (QueryResultEntry result : results) {
                registerCitation(result);
            }

            return formatResults(results);
        } catch (Exception e) {
            log.warn("[RagSearchTool] 检索异常: {}", e.getMessage());
            return "知识库检索暂时不可用：" + e.getMessage();
        }
    }

    private List<String> resolveQueries(String question, List<String> kbCodes) {
        if (queryRewriteService == null) return List.of(question);
        try {
            QueryRewriteService.QueryRewriteContext ctx =
                    new QueryRewriteService.QueryRewriteContext(kbCodes.isEmpty() ? null : kbCodes);
            QueryRewriteService.QueryRewriteResult result =
                    queryRewriteService.generateQueries(question, 3, ctx);
            List<String> queries = new ArrayList<>();
            if (result.getMainQuery() != null && !result.getMainQuery().isBlank())
                queries.add(result.getMainQuery().trim());
            if (result.getExpandedQueries() != null)
                result.getExpandedQueries().stream()
                      .filter(q -> q != null && !q.isBlank())
                      .map(String::trim)
                      .forEach(queries::add);
            return queries.isEmpty() ? List.of(question) : queries;
        } catch (Exception e) {
            log.warn("[RagSearchTool] 查询改写失败，降级为原始问题: {}", e.getMessage());
            return List.of(question);
        }
    }

    private List<String> parseKnowledgeCodes(String knowledgeCodes) {
        if (knowledgeCodes == null || knowledgeCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(knowledgeCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String formatResults(List<QueryResultEntry> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("知识库检索结果（共").append(results.size()).append("条）：\n\n");

        for (int i = 0; i < results.size(); i++) {
            QueryResultEntry entry = results.get(i);
            String block = formatEntry(entry, i + 1);

            if (sb.length() + block.length() > MAX_RESULT_CHARS) {
                int remaining = MAX_RESULT_CHARS - sb.length();
                if (remaining > 100) {
                    sb.append(block, 0, Math.min(remaining - 20, block.length())).append("...\n");
                }
                break;
            }
            sb.append(block).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String formatEntry(QueryResultEntry entry, int index) {
        String docName = entry.getDocument() != null ? entry.getDocument().getName() : "未知文档";
        String kb = entry.getKnowledgeCode() != null ? entry.getKnowledgeCode() : "";
        Map<String, Object> chunk = entry.getChunkContent();

        String sectionTitle = chunk != null && chunk.get("section_title") != null
                ? String.valueOf(chunk.get("section_title")) : "";

        String header;
        if (!sectionTitle.isBlank()) {
            header = String.format("【知识片段】[知识库: %s] [文档: %s] [章节: %s]", kb, docName, sectionTitle);
        } else {
            header = String.format("【知识片段】[知识库: %s] [文档: %s]", kb, docName);
        }

        String body = formatChunkContent(chunk);
        return header + "\n" + body;
    }

    private String formatChunkContent(Map<String, Object> chunkContent) {
        if (chunkContent == null || chunkContent.isEmpty()) {
            return "";
        }
        return chunkContent.entrySet().stream()
                .filter(e -> e.getValue() != null && !String.valueOf(e.getValue()).isBlank())
                .filter(e -> !"section_title".equals(e.getKey()))
                .map(e -> e.getValue().toString())
                .collect(Collectors.joining("\n"));
    }

    private void registerCitation(QueryResultEntry entry) {
        CitationItem item = new CitationItem();
        item.setType(CitationItem.CitationType.KB);
        item.setKnowledgeCode(entry.getKnowledgeCode());
        item.setScore(entry.getScore());
        item.setChunkContent(entry.getChunkContent());
        if (entry.getDocument() != null) {
            item.setDocumentName(entry.getDocument().getName());
        }
        CitationRegistry.register(item);
    }
}
