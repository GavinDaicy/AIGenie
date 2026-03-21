package com.genie.query.infrastructure.lock;

import com.genie.query.domain.common.LockManager;
import com.genie.query.infrastructure.util.LockDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 基于现有 LockDelegate 的分布式锁适配实现。
 *
 * @author daicy
 * @date 2026/3/9
 */
@Component
public class RedisLockManager implements LockManager {

    @Autowired
    private LockDelegate lockDelegate;

    @Override
    public void withLock(String lockKey, Runnable action) {
        lockDelegate.execInLock(action, lockKey);
    }

    @Override
    public <T> T withLockAndResult(String lockKey, Supplier<T> supplier) {
        return lockDelegate.execInLock(supplier, lockKey);
    }
}

