package com.genie.query.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置，用于文档解析、切块、向量化等耗时操作的后台执行。
 *
 * @author daicy
 * @date 2026/3/11
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文档处理专用线程池（解析、切块、向量化）。
     * 线程名前缀便于排查问题，队列容量和拒绝策略可后续按需调整。
     */
    @Bean(name = "documentTaskExecutor")
    public Executor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
