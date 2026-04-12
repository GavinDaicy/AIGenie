package com.genie.query.domain.agent;

/**
 * 预生成 MessageId 的 ThreadLocal 持有者。
 *
 * <p>AgentApplication 在 Agent 执行前预生成 Snowflake ID，存入此 Holder；
 * AgentOrchestratorImpl 在推送 FINAL_ANSWER 事件时读取该 ID 一并携带；
 * AgentApplication 落库时将同一 ID 设置到 ChatMessage，确保 SSE 事件与 DB 记录一致。
 *
 * @author daicy
 * @date 2026/4/12
 */
public class PendingMessageIdHolder {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static void set(String messageId) {
        HOLDER.set(messageId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
