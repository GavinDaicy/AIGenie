package com.genie.query.domain.agent;

import com.genie.query.domain.agent.citation.CitationItem;

import java.util.Collections;
import java.util.List;

/**
 * Agent 执行结果：包含最终答案文本和本轮引用数据列表。
 *
 * @author daicy
 */
public class AgentResult {

    private final String finalAnswer;
    private final List<CitationItem> citations;

    public AgentResult(String finalAnswer, List<CitationItem> citations) {
        this.finalAnswer = finalAnswer;
        this.citations = citations != null ? citations : Collections.emptyList();
    }

    public static AgentResult of(String finalAnswer, List<CitationItem> citations) {
        return new AgentResult(finalAnswer, citations);
    }

    public static AgentResult paused() {
        return new AgentResult(null, Collections.emptyList());
    }

    public String getFinalAnswer() { return finalAnswer; }

    public List<CitationItem> getCitations() { return citations; }

    public boolean hasFinalAnswer() { return finalAnswer != null; }
}
