package com.genie.query.infrastructure.cache;

import com.genie.query.domain.cache.CacheService;
import com.genie.query.infrastructure.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的通用缓存实现。
 *
 * @author daicy
 * @date 2026/3/21
 */
@Service
public class RedisCacheServiceImpl implements CacheService {

    @Autowired(required = false)
    private RedisUtil redisUtil;

    @Override
    public <T> T get(String key) {
        if (redisUtil == null) {
            return null;
        }
        return redisUtil.get(key);
    }

    @Override
    public void set(String key, Object value) {
        if (redisUtil == null) {
            return;
        }
        redisUtil.set(key, value);
    }

    @Override
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (redisUtil == null) {
            return;
        }
        redisUtil.set(key, value, timeout, unit);
    }

    @Override
    public boolean delete(String key) {
        if (redisUtil == null) {
            return false;
        }
        return redisUtil.delete(key);
    }
}
