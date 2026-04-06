package com.genie.query.domain.agent.event;

import java.io.PrintWriter;

/**
 * 步骤事件发布接口：将 Agent 推理步骤事件推送到 SSE 连接。
 *
 * <p>具体实现（基础设施层）负责 JSON 序列化，并直接写入响应 PrintWriter + flush，
 * 确保每个事件立即到达 TCP socket，不经过 Spring SseEmitter 缓冲层。
 *
 * @author daicy
 * @date 2026/4/2
 */
public interface StepEventPublisher {

    /**
     * 发布一个步骤事件到 SSE 连接。
     *
     * @param writer 响应流写入器（由 AgentController 从 HttpServletResponse 获取）
     * @param event  要推送的步骤事件
     */
    void publish(PrintWriter writer, StepEvent event);

    /**
     * 发送流结束标记 [DONE]，通知前端 Agent 执行完毕。
     *
     * @param writer 响应流写入器
     */
    void sendDone(PrintWriter writer);
}
