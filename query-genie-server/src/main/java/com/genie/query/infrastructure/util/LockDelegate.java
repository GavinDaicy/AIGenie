package com.genie.query.infrastructure.util;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Slf4j
@Component
public class LockDelegate {

    @Autowired
    private RedisUtil redisUtil;

    public <R> R execInLock(Supplier<R> supplier, String key) {
        RLock lock = redisUtil.getLock(key);
        try {
            boolean isLocked = lock.tryLock(10,30, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    return supplier.get();
                } finally {
                    lock.unlock();
                }
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("execute supplier execInLock -- sleep error:{1}", e);
                }
                return execInLock(supplier, key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁失败", e);
        }
    }


    public void execInLock(Runnable runnable, String key) {
        RLock lock = redisUtil.getLock(key);
        try {
            boolean isLocked = lock.tryLock(10,30, TimeUnit.SECONDS);
            if (isLocked) {
                try {
                    runnable.run();
                } finally {
                    lock.unlock();
                }
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("execute runnable execInLock -- sleep error:{1}", e);
                }
                execInLock(runnable, key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁失败", e);
        }
    }
}
