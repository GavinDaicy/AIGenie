package com.genie.query.domain.agent.middleware;

import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;

/**
 * Agent 中间件接口：包裹在 ReAct 循环外围，处理横切关注点。
 *
 * <p>执行顺序（由 MiddlewareChain 控制）：
 * <pre>
 *   before(context) × N（按 order 升序）
 *       ↓
 *   ReAct 核心循环
 *       ↓
 *   after(context, result) × N（按 order 降序）
 * </pre>
 *
 * <p>实现规范：
 * <ul>
 *   <li>before/after/onAskUserPause 均有默认空实现，子类只重写需要的方法</li>
 *   <li>中间件不应抛出受检异常，内部 catch 并记日志</li>
 *   <li>getOrder() 必须通过具名常量返回，禁止直接返回魔法数字</li>
 * </ul>
 *
 * @author daicy
 */
public interface AgentMiddleware {

    /**
     * 返回中间件名称，用于日志和配置。
     */
    String getName();

    /**
     * ReAct 循环开始前执行：注入上下文、读取缓存、初始化状态等。
     */
    default void before(AgentContext context) {}

    /**
     * ReAct 循环结束后执行：保存结果、更新状态、清理资源等。
     * 注意：即使 ReAct 循环抛出异常，after 也会被调用（由 MiddlewareChain 保障）。
     */
    default void after(AgentContext context, AgentResult result) {}

    /**
     * ReAct 循环因 askUser 工具暂停时的钩子。
     * 由编排器在 askUser 分支调用，替代直接操作 Redis 的方式。
     *
     * @param context         当前执行上下文
     * @param toolResultsHint 本轮已执行工具结果摘要（用于续跑时注入）
     */
    default void onAskUserPause(AgentContext context, String toolResultsHint) {}

    /**
     * 执行顺序权重：数字越小越先执行 before，越后执行 after。
     * 实现类必须通过具名常量（如 {@code public static final int ORDER = 10}）返回此值。
     */
    default int getOrder() { return 100; }
}
