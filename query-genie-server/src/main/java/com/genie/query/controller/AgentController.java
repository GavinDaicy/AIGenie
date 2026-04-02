package com.genie.query.controller;

import com.genie.query.application.AgentApplication;
import com.genie.query.controller.dto.AgentAskRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * Agent 问答控制器：提供 SSE 流式 Agent 推理接口。
 *
 * <p>接口：POST /genie/api/agent/ask/stream
 *
 * <p>返回 SSE 事件流，每个事件为一个推理步骤（THOUGHT/TOOL_CALL/TOOL_RESULT/FINAL_ANSWER），
 * 最终以 [DONE] 标记结束。
 *
 * @author daicy
 * @date 2026/4/2
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final long SSE_TIMEOUT_MS = 120_000L;

    @Autowired
    private AgentApplication agentApplication;

    @Qualifier("agentTaskExecutor")
    @Autowired
    private Executor agentTaskExecutor;

    /**
     * Agent 流式问答接口。
     *
     * <pre>
     * POST /genie/api/agent/ask/stream
     * Content-Type: application/json
     * {
     *   "question": "近半年直径20钢筋哪家供应商价格最低",
     *   "sessionId": "session-123",
     *   "datasourceIds": [1],
     *   "knowledgeCodes": ["steel-kb"]
     * }
     * </pre>
     *
     * @param request Agent 问答请求
     * @return SSE 事件流（THOUGHT / TOOL_CALL / TOOL_RESULT / FINAL_ANSWER / ERROR）
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AgentAskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        log.info("[AgentController] 收到请求 | sessionId={} | question={}",
                request.getSessionId(), request.getQuestion());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitter.onTimeout(() -> {
            log.warn("[AgentController] SSE 连接超时 | sessionId={}", request.getSessionId());
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.warn("[AgentController] SSE 连接异常 | error={}", ex.getMessage());
        });

        agentTaskExecutor.execute(() -> agentApplication.handleQuestion(request, emitter));

        return emitter;
    }
}
