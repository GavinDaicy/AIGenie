package com.genie.query.domain.agent.middleware;

import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MiddlewareChain 单元测试：覆盖执行顺序、异常隔离、pause 钩子三个场景。
 */
class MiddlewareChainTest {

    private static final List<String> callLog = new ArrayList<>();

    // ──────────────────────────────────────────────
    // 测试用中间件桩
    // ──────────────────────────────────────────────

    static class OrderTenMiddleware implements AgentMiddleware {
        public static final int ORDER = 10;
        @Override public String getName() { return "order10"; }
        @Override public int getOrder() { return ORDER; }
        @Override public void before(AgentContext ctx) { callLog.add("before:10"); }
        @Override public void after(AgentContext ctx, AgentResult r) { callLog.add("after:10"); }
    }

    static class OrderTwentyMiddleware implements AgentMiddleware {
        public static final int ORDER = 20;
        @Override public String getName() { return "order20"; }
        @Override public int getOrder() { return ORDER; }
        @Override public void before(AgentContext ctx) { callLog.add("before:20"); }
        @Override public void after(AgentContext ctx, AgentResult r) { callLog.add("after:20"); }
    }

    static class OrderThirtyMiddleware implements AgentMiddleware {
        public static final int ORDER = 30;
        @Override public String getName() { return "order30"; }
        @Override public int getOrder() { return ORDER; }
        @Override public void before(AgentContext ctx) { callLog.add("before:30"); }
        @Override public void after(AgentContext ctx, AgentResult r) { callLog.add("after:30"); }
    }

    static class ExplodingMiddleware implements AgentMiddleware {
        @Override public String getName() { return "exploding"; }
        @Override public int getOrder() { return 15; }
        @Override public void before(AgentContext ctx) {
            callLog.add("before:exploding");
            throw new RuntimeException("模拟中间件异常");
        }
        @Override public void after(AgentContext ctx, AgentResult r) {
            callLog.add("after:exploding");
            throw new RuntimeException("模拟中间件 after 异常");
        }
    }

    static class PauseAwareMiddleware implements AgentMiddleware {
        @Override public String getName() { return "pauseAware"; }
        @Override public void onAskUserPause(AgentContext ctx, String hint) {
            callLog.add("pause:" + hint);
        }
    }

    // ──────────────────────────────────────────────
    // 被测对象
    // ──────────────────────────────────────────────

    private MiddlewareChain chain;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        callLog.clear();
        chain = new MiddlewareChain();
        context = new AgentContext("s1", "question", List.of(), List.of(), null);
    }

    // ──────────────────────────────────────────────
    // 场景 1：before 按 order 升序执行
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景1: runBefore() 按 order 升序调用各中间件")
    void runBeforeExecutesInAscendingOrder() {
        setMiddlewares(List.of(new OrderThirtyMiddleware(), new OrderTenMiddleware(), new OrderTwentyMiddleware()));

        chain.runBefore(context);

        assertThat(callLog).containsExactly("before:10", "before:20", "before:30");
    }

    // ──────────────────────────────────────────────
    // 场景 2：after 按 order 降序执行
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景2: runAfter() 按 order 降序调用各中间件")
    void runAfterExecutesInDescendingOrder() {
        setMiddlewares(List.of(new OrderTenMiddleware(), new OrderThirtyMiddleware(), new OrderTwentyMiddleware()));

        chain.runAfter(context, AgentResult.of("answer", List.of()));

        assertThat(callLog).containsExactly("after:30", "after:20", "after:10");
    }

    // ──────────────────────────────────────────────
    // 场景 3：某中间件抛异常，不中断后续中间件
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景3: 中间件抛异常时，后续中间件仍正常执行")
    void exceptionInMiddlewareDoesNotStopChain() {
        setMiddlewares(List.of(new OrderTenMiddleware(), new ExplodingMiddleware(), new OrderThirtyMiddleware()));

        chain.runBefore(context);

        assertThat(callLog).containsExactly("before:10", "before:exploding", "before:30");
    }

    // ──────────────────────────────────────────────
    // 场景 4：onAskUserPause 钩子被正确调用
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景4: runOnAskUserPause() 调用所有中间件的 onAskUserPause 钩子")
    void runOnAskUserPauseCallsAllMiddlewares() {
        setMiddlewares(List.of(new OrderTenMiddleware(), new PauseAwareMiddleware()));

        chain.runOnAskUserPause(context, "工具结果：xxx");

        assertThat(callLog).contains("pause:工具结果：xxx");
    }

    // ──────────────────────────────────────────────
    // 辅助：注入 middlewares 列表并触发 @PostConstruct 逻辑
    // ──────────────────────────────────────────────

    private void setMiddlewares(List<AgentMiddleware> middlewares) {
        ReflectionTestUtils.setField(chain, "middlewares", new ArrayList<>(middlewares));
        chain.init();
    }
}
