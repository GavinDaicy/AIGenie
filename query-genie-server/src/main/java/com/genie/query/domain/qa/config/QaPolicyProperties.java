package com.genie.query.domain.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QA 业务策略配置（与具体 LLM 供应商无关）。
 */
@Data
@ConfigurationProperties(prefix = "app.qa")
public class QaPolicyProperties {
    /** 是否启用智能问答，默认 true */
    private boolean enabled = true;
    /** 用于上下文的检索条数，默认 10 */
    private int size = 10;
    /** 是否对检索结果做 rerank，默认 true */
    private boolean rerank = true;
    /** 上下文最大字符数，避免超出模型窗口，默认 6000 */
    private int maxContextChars = 6000;
    /** 参与上下文的历史对话轮数上限（每轮为 user+assistant 一对），默认 10 */
    private int maxHistoryTurns = 10;
    /** 超过该轮数时对更早的历史做总结压缩，默认 6 */
    private int summarizeWhenTurnsOver = 6;
    /** 历史内容总字符数上限（可选），超过时触发总结，0 表示不按字符数限制 */
    private int maxTotalHistoryChars = 0;
}
