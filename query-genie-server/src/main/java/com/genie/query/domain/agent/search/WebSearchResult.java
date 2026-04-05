package com.genie.query.domain.agent.search;

/**
 * 联网搜索统一结果 DTO，各 Provider 实现均映射到此结构。
 *
 * @author daicy
 */
public class WebSearchResult {

    private String title;
    private String url;
    private String snippet;
    private String source;
    private String publishedTime;

    public WebSearchResult() {}

    public WebSearchResult(String title, String url, String snippet, String source, String publishedTime) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.source = source;
        this.publishedTime = publishedTime;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getPublishedTime() { return publishedTime; }
    public void setPublishedTime(String publishedTime) { this.publishedTime = publishedTime; }

    @Override
    public String toString() {
        return "WebSearchResult{title='" + title + "', url='" + url + "', source='" + source + "'}";
    }
}
