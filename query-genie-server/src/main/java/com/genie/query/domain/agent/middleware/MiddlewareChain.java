package com.genie.query.domain.agent.middleware;

import com.genie.query.domain.agent.orchestration.AgentContext;
import com.genie.query.domain.agent.orchestration.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 中间件执行链：按 order 升序执行 before，按 order 降序执行 after。
 *
 * <p>异常处理：每个中间件的异常被独立 catch 并记录日志，不会中断后续中间件的执行。
 *
 * @author daicy
 */
@Component
public class MiddlewareChain {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareChain.class);

    @Autowired(required = false)
    private List<AgentMiddleware> middlewares;

    @PostConstruct
    public void init() {
        if (middlewares != null) {
            middlewares.sort(Comparator.comparingInt(AgentMiddleware::getOrder));
            log.info("[MiddlewareChain] 已注册中间件（按 order 排序）: {}",
                    middlewares.stream().map(m -> m.getName() + "(" + m.getOrder() + ")").toList());
        } else {
            middlewares = Collections.emptyList();
        }
    }

    /**
     * 按 order 升序执行所有中间件的 before。
     * 某个中间件抛出异常时，记录日志后继续执行后续中间件。
     */
    public void runBefore(AgentContext context) {
        for (AgentMiddleware m : middlewares) {
            try {
                m.before(context);
            } catch (Exception e) {
                log.warn("[MiddlewareChain] {} before 执行异常，跳过 | error={}", m.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 按 order 降序执行所有中间件的 after。
     * 某个中间件抛出异常时，记录日志后继续执行后续中间件。
     */
    public void runAfter(AgentContext context, AgentResult result) {
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware m = middlewares.get(i);
            try {
                m.after(context, result);
            } catch (Exception e) {
                log.warn("[MiddlewareChain] {} after 执行异常，跳过 | error={}", m.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 遍历所有中间件，调用 onAskUserPause 钩子。
     * 仅 PausedContextMiddleware 等有状态的中间件需要实现此钩子。
     */
    public void runOnAskUserPause(AgentContext context, String toolResultsHint) {
        for (AgentMiddleware m : middlewares) {
            try {
                m.onAskUserPause(context, toolResultsHint);
            } catch (Exception e) {
                log.warn("[MiddlewareChain] {} onAskUserPause 执行异常，跳过 | error={}", m.getName(), e.getMessage(), e);
            }
        }
    }
}
