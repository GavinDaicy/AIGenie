package com.genie.query.domain.qa.service;

import com.genie.query.domain.qa.model.AnswerResult;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.query.model.QueryResultEntry;

import java.util.List;

/**
 * 答案生成领域服务：根据问题与检索得到的条目，构建 prompt 并调用大模型生成答案。
 * 不依赖具体 LLM 实现，由 infrastructure 层提供实现（如 DashScope）。
 *
 * @author daicy
 * @date 2026/3/8
 */
public interface AnswerService {

    /**
     * 根据用户问题与检索上下文生成答案（单轮，无历史）。
     *
     * @param question        用户问题
     * @param contextEntries  检索得到的条目，作为参考上下文
     * @return 答案正文与引用来源
     */
    AnswerResult generate(String question, List<QueryResultEntry> contextEntries);

    /**
     * 根据用户问题、检索上下文与历史对话轮次生成答案（多轮）。
     *
     * @param question        当前用户问题
     * @param contextEntries  当前轮检索得到的条目，作为参考上下文
     * @param history         历史对话轮次（仅 role + content），可为空
     * @return 答案正文与引用来源
     */
    AnswerResult generate(String question, List<QueryResultEntry> contextEntries, List<ChatTurn> history);

    /**
     * 流式生成答案：通过回调按块推送文本，结束时回调 onComplete。
     * 若无流式能力则退化为一次性生成后通过 onChunk(fullAnswer) 与 onComplete 回调。
     *
     * @param question        用户问题
     * @param contextEntries  检索得到的条目，作为参考上下文
     * @param history         历史对话轮次，可为空
     * @param callback        流式回调
     */
    void streamGenerate(String question, List<QueryResultEntry> contextEntries,
                        List<ChatTurn> history, StreamCallback callback);
}
