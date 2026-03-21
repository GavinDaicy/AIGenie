package com.genie.query.infrastructure.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedissonClient redissonClient;

    /**
     * 设置字符串值
     */
    public void set(String key, Object value) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value);
    }

    /**
     * 设置带过期时间的字符串值
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        bucket.set(value, timeout, unit);
    }

    /**
     * 获取字符串值
     */
    public <T> T get(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }

    /**
     * 删除键
     */
    public boolean delete(String key) {
        return redissonClient.getBucket(key).delete();
    }

    /**
     * 获取分布式锁
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 获取公平锁
     */
    public RLock getFairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 获取读写锁
     */
    public RReadWriteLock getReadWriteLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey);
    }

    /**
     * 获取Map
     */
    public <K, V> RMap<K, V> getMap(String mapName) {
        return redissonClient.getMap(mapName);
    }

    /**
     * 获取Set
     */
    public <V> RSet<V> getSet(String setName) {
        return redissonClient.getSet(setName);
    }

    /**
     * 获取List
     */
    public <V> RList<V> getList(String listName) {
        return redissonClient.getList(listName);
    }

    /**
     * 获取队列
     */
    public <V> RQueue<V> getQueue(String queueName) {
        return redissonClient.getQueue(queueName);
    }

    /**
     * 获取延迟队列
     */
    public <V> RDelayedQueue<V> getDelayedQueue(RQueue<V> destinationQueue) {
        return redissonClient.getDelayedQueue(destinationQueue);
    }

    /**
     * 获取原子Long
     */
    public RAtomicLong getAtomicLong(String name) {
        return redissonClient.getAtomicLong(name);
    }

    /**
     * 获取布隆过滤器
     */
    public <V> RBloomFilter<V> getBloomFilter(String name) {
        return redissonClient.getBloomFilter(name);
    }
}