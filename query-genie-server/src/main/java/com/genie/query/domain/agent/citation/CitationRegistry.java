package com.genie.query.domain.agent.citation;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 工具引用数据的线程级注册表（ThreadLocal）。
 *
 * <p>工具执行时通过 {@link #register} 写入本次调用产生的引用数据；
 * AgentOrchestratorImpl 在每次工具调用结束后通过 {@link #drainAndClear} 取出并分配编号。
 * 请求结束时必须调用 {@link #cleanup} 防止 ThreadLocal 泄漏。
 *
 * @author daicy
 */
public final class CitationRegistry {

    private static final ThreadLocal<List<CitationItem>> HOLDER =
            ThreadLocal.withInitial(ArrayList::new);

    /** 全任务全局递增引用编号（每次 execute() 通过 cleanup() 重置） */
    private static final ThreadLocal<int[]> COUNTER =
            ThreadLocal.withInitial(() -> new int[]{0});

    private CitationRegistry() {}

    /**
     * 工具内部调用：注册一条引用数据（index 由 Orchestrator 统一分配，此时可留 0）。
     */
    public static void register(CitationItem item) {
        HOLDER.get().add(item);
    }

    /**
     * Orchestrator 调用：为当前 CitationItem 分配下一个全局递增编号并返回。
     */
    public static int nextIndex() {
        return ++COUNTER.get()[0];
    }

    /**
     * Orchestrator 调用：取出当前注册的所有引用项并清空，供分配编号使用。
     */
    public static List<CitationItem> drainAndClear() {
        List<CitationItem> current = new ArrayList<>(HOLDER.get());
        HOLDER.get().clear();
        return current;
    }

    /**
     * 请求结束时必须调用，防止 ThreadLocal 在线程池复用时泄漏。
     */
    public static void cleanup() {
        HOLDER.remove();
        COUNTER.remove();
    }
}
