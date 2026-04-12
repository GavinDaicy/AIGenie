package com.genie.query.domain.agent.routing;

import java.util.Collections;
import java.util.List;

/**
 * 线程本地近期对话历史持有者。
 *
 * <p>用于在同一请求线程内从 AgentApplication 向 RagSearchTool 等工具传递最近历史，
 * 避免跨层参数透传。使用后必须在 finally 块中调用 {@link #clear()}。
 *
 * @author daicy
 * @date 2026/4/12
 */
public final class RecentHistoryHolder {

    private static final ThreadLocal<List<String>> HOLDER =
            ThreadLocal.withInitial(Collections::emptyList);

    private RecentHistoryHolder() {}

    public static void set(List<String> history) {
        HOLDER.set(history != null ? history : Collections.emptyList());
    }

    public static List<String> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
