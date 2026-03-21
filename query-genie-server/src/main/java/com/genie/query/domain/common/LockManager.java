package com.genie.query.domain.common;

import java.util.function.Supplier;

/**
 * 领域层分布式锁抽象。
 * <p>
 * 由基础设施层提供具体实现（如基于 Redis 的锁），领域服务通过该接口
 * 保证关键操作的互斥执行，而不感知底层中间件细节。
 *
 * @author daicy
 * @date 2026/3/9
 */
public interface LockManager {

    /**
     * 在指定锁资源下执行动作。
     *
     * @param lockKey 锁 key
     * @param action  要执行的动作
     */
    void withLock(String lockKey, Runnable action);

    /**
     * 在指定锁资源下执行动作并返回结果。
     *
     * @param lockKey 锁 key
     * @param supplier 计算逻辑
     * @param <T> 结果类型
     * @return 结果
     */
    <T> T withLockAndResult(String lockKey, Supplier<T> supplier);
}

