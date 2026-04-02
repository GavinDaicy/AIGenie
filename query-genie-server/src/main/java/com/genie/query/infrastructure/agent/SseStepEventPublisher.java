package com.genie.query.infrastructure.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genie.query.domain.agent.StepEvent;
import com.genie.query.domain.agent.StepEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 基于 SSE（Server-Sent Events）的步骤事件发布实现。
 *
 * <p>将 {@link StepEvent} 序列化为 JSON 并通过 {@link SseEmitter} 推送给前端。
 * 写入失败时仅打印警告日志，不抛出异常，保证主流程不受 SSE 断连影响。
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
    public void publish(SseEmitter emitter, StepEvent event) {
        if (emitter == null || event == null) return;
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .name("step")
                    .data(json));
            log.debug("[SsePublisher] 推送事件 | type={} | iteration={}",
                    event.getType(), event.getIteration());
        } catch (JsonProcessingException e) {
            log.warn("[SsePublisher] 事件序列化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[SsePublisher] SSE推送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    @Override
    public void sendDone(SseEmitter emitter) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            log.warn("[SsePublisher] 发送 [DONE] 失败: {}", e.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }
}
