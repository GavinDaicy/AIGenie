package com.genie.query.domain.agent.tool;

import com.genie.query.domain.agent.citation.CitationItem;
import com.genie.query.domain.agent.citation.CitationRegistry;
import com.genie.query.domain.agent.search.WebSearchProvider;
import com.genie.query.domain.agent.search.WebSearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 联网搜索工具：封装 {@link WebSearchProvider}，供 Spring AI Agent 通过 Function Calling 调用。
 *
 * <p>工具触发场景：知识库和数据库均无答案，需要获取实时互联网信息时。
 * <p>仅当 {@code app.web-search.enabled=true}（默认）且 {@link WebSearchProvider} Bean 存在时注册此工具。
 *
 * @author daicy
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private static final String UNAVAILABLE = "SEARCH_UNAVAILABLE";
    private static final int MAX_SNIPPET_CHARS = 300;
    private static final int MAX_RESULT_CHARS = 3000;

    @Autowired(required = false)
    private WebSearchProvider webSearchProvider;

    @Value("${app.web-search.max-results:5}")
    private int maxResults;

    /**
     * 搜索互联网，获取实时信息。
     * 适用于知识库和数据库均无答案的问题，如最新行情、时事新闻、外部产品信息等。
     *
     * @param question 用户的自然语言问题（工具会自动提取搜索关键词）
     * @return 搜索摘要文本（含来源 URL 列表）；搜索不可用时返回 SEARCH_UNAVAILABLE
     */
    @Tool(description = "搜索互联网，获取实时信息。仅当知识库和数据库均无法回答时使用，例如：最新市场行情、时事新闻、外部产品信息、实时价格等。不要对可从知识库或数据库获取的内部信息使用此工具。")
    public String searchWeb(
            @ToolParam(description = "要搜索的关键词或问题，建议简洁精确，不超过50字") String question) {

        log.info("[WebSearchTool] 开始联网搜索 | question={}", question);

        if (webSearchProvider == null) {
            log.warn("[WebSearchTool] 未配置 WebSearchProvider，返回不可用");
            return UNAVAILABLE;
        }

        try {
            List<WebSearchResult> results = webSearchProvider.search(question, maxResults);

            if (results == null || results.isEmpty()) {
                log.info("[WebSearchTool] 未搜索到结果");
                return "联网搜索未找到与问题相关的内容，请尝试换用其他工具或调整问题描述。";
            }

            log.info("[WebSearchTool] 搜索到 {} 条结果", results.size());
            for (WebSearchResult result : results) {
                registerCitation(result);
            }
            return formatResults(results);

        } catch (Exception e) {
            log.warn("[WebSearchTool] 搜索异常，降级返回不可用标记: {}", e.getMessage());
            return UNAVAILABLE;
        }
    }

    private void registerCitation(WebSearchResult r) {
        CitationItem item = new CitationItem();
        item.setType(CitationItem.CitationType.WEB);
        item.setTitle(r.getTitle());
        item.setUrl(r.getUrl());
        item.setSnippet(r.getSnippet());
        item.setSource(r.getSource());
        item.setPublishedTime(r.getPublishedTime());
        CitationRegistry.register(item);
    }

    private String formatResults(List<WebSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("联网搜索结果（共").append(results.size()).append("条）：\n\n");

        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            sb.append("【网页结果】\n");

            if (r.getTitle() != null && !r.getTitle().isBlank()) {
                sb.append("标题：").append(r.getTitle()).append("\n");
            }
            if (r.getUrl() != null && !r.getUrl().isBlank()) {
                sb.append("来源：").append(r.getUrl()).append("\n");
            }
            if (r.getPublishedTime() != null && !r.getPublishedTime().isBlank()) {
                sb.append("时间：").append(r.getPublishedTime()).append("\n");
            }
            if (r.getSnippet() != null && !r.getSnippet().isBlank()) {
                String snippet = r.getSnippet().length() > MAX_SNIPPET_CHARS
                        ? r.getSnippet().substring(0, MAX_SNIPPET_CHARS) + "..."
                        : r.getSnippet();
                sb.append("摘要：").append(snippet).append("\n");
            }
            sb.append("\n");

            if (sb.length() > MAX_RESULT_CHARS) {
                sb.append("（更多结果已省略）\n");
                break;
            }
        }

        return sb.toString().trim();
    }
}
