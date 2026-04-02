package com.genie.query.domain.agent;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 步骤事件发布接口：将 Agent 推理步骤事件推送到 SSE 连接。
 *
 * <p>具体实现（基础设施层）负责 JSON 序列化和 SseEmitter 写入。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface StepEventPublisher {

    /**
     * 发布一个步骤事件到 SSE 连接。
     *
     * @param emitter SSE 推送器（由 AgentController 创建并传入）
     * @param event   要推送的步骤事件
     */
    void publish(SseEmitter emitter, StepEvent event);

    /**
     * 发送流结束标记 [DONE]，通知前端 Agent 执行完毕。
     *
     * @param emitter SSE 推送器
     */
    void sendDone(SseEmitter emitter);
}
