package com.genie.query.domain.cache;

import java.util.concurrent.TimeUnit;

/**
 * 通用缓存能力抽象，由基础设施层提供具体实现。
 *
 * @author daicy
 * @date 2026/3/21
 */
public interface CacheService {

    /**
     * 读取缓存。
     */
    <T> T get(String key);

    /**
     * 写入缓存（无过期时间）。
     */
    void set(String key, Object value);

    /**
     * 写入缓存（带过期时间）。
     */
    void set(String key, Object value, long timeout, TimeUnit unit);

    /**
     * 删除缓存。
     */
    boolean delete(String key);
}
