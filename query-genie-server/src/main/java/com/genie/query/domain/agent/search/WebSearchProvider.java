package com.genie.query.domain.agent.search;

import java.util.List;

/**
 * 联网搜索 Provider 接口。
 *
 * <p>通过 {@code app.web-search.provider} 配置选择具体实现：
 * <ul>
 *   <li>{@code bocha}   - 博查AI（默认，国内合规）</li>
 *   <li>{@code baidu}   - 百度AI搜索（千帆平台）</li>
 *   <li>{@code ali-iqs} - 阿里云IQS通用搜索</li>
 *   <li>{@code searxng} - SearXNG自托管（免费，无API Key）</li>
 * </ul>
 *
 * @author daicy
 */
public interface WebSearchProvider {

    /**
     * 执行网页搜索。
     *
     * @param query      搜索关键词
     * @param maxResults 最多返回的结果条数
     * @return 搜索结果列表，失败时返回空列表
     */
    List<WebSearchResult> search(String query, int maxResults);
}
