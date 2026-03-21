package com.genie.query.infrastructure.util.id;

import com.genie.query.domain.common.IdGenerator;
import com.genie.query.infrastructure.util.snowflake.SnowflakeIdUtils;
import org.springframework.stereotype.Component;

/**
 * 基于现有 SnowflakeIdUtils 的 IdGenerator 实现。
 *
 * @author daicy
 * @date 2026/3/9
 */
@Component
public class SnowflakeIdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return SnowflakeIdUtils.getNextStringId();
    }
}

