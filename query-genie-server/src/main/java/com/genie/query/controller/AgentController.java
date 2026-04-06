package com.genie.query.controller;

import com.genie.query.application.AgentApplication;
import com.genie.query.controller.dto.AgentAskRequest;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
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
    @PostMapping("/ask/stream")
    public void askStream(@RequestBody AgentAskRequest request,
                          HttpServletRequest httpRequest,
                          HttpServletResponse httpResponse) throws Exception {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }

        log.info("[AgentController] 收到请求 | sessionId={} | question={}",
                request.getSessionId(), request.getQuestion());

        // 设置 SSE 响应头，直接刷出，让浏览器立即确认连接
        httpResponse.setContentType("text/event-stream;charset=UTF-8");
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("X-Accel-Buffering", "no");

        // 启动 Servlet 异步模式，Servlet 线程不阻塞
        AsyncContext asyncContext = httpRequest.startAsync();
        asyncContext.setTimeout(SSE_TIMEOUT_MS);

        // 获取 PrintWriter：Tomcat 直接写入底层 socket，flush() 立即到 TCP
        PrintWriter writer = httpResponse.getWriter();
        // 发送一个注释行，触发响应头立即提交到浏览器（确保 fetch().then() 即时 resolve）
        writer.write(": ping\n\n");
        writer.flush();

        agentTaskExecutor.execute(() -> {
            try {
                agentApplication.handleQuestion(request, writer);
            } catch (Exception e) {
                log.error("[AgentController] Agent 执行异常 | error={}", e.getMessage(), e);
            } finally {
                asyncContext.complete();
            }
        });
    }
}
