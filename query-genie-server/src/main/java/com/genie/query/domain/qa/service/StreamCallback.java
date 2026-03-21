package com.genie.query.domain.qa.service;

import com.genie.query.domain.query.model.QueryResultEntry;

import java.util.List;

/**
 * 流式答案生成回调：按块接收文本，结束时收到完整答案与来源。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface StreamCallback {

    /**
     * 收到一段答案片段（增量）。
     */
    void onChunk(String chunk);

    /**
     * 流式结束，返回完整答案与引用来源（与 generate 返回的 sources 一致）。
     */
    void onComplete(String fullAnswer, List<QueryResultEntry> sources);

    /**
     * 发生错误时调用。
     */
    default void onError(Throwable t) {
        // no-op，实现类可按需覆盖
    }
}
