package com.genie.query.infrastructure.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.agent.event.StepEvent;
import com.genie.query.domain.agent.event.StepEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;

/**
 * 基于 SSE（Server-Sent Events）的步骤事件发布实现。
 *
 * <p>直接写入 {@link PrintWriter}（来自 HttpServletResponse.getWriter()）并立即 flush，
 * 绕过 Spring SseEmitter 的所有缓冲层，确保每个事件实时到达 TCP socket。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class SseStepEventPublisher implements StepEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseStepEventPublisher.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void publish(PrintWriter writer, StepEvent event) {
        if (writer == null || event == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            writer.write("event:step\ndata:" + json + "\n\n");
            writer.flush();
            log.debug("[SsePublisher] 推送事件 | type={} | iteration={}",
                    event.getType(), event.getIteration());
        } catch (JsonProcessingException e) {
            log.warn("[SsePublisher] 事件序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[SsePublisher] SSE推送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    @Override
    public void sendDone(PrintWriter writer) {
        if (writer == null) return;
        writer.write("event:done\ndata:[DONE]\n\n");
        writer.flush();
    }
}
