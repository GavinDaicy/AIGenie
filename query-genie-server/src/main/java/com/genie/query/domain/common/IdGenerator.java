package com.genie.query.domain.common;

/**
 * 领域层 ID 生成抽象。
 * <p>
 * 由基础设施层提供具体实现（如 SnowflakeIdGenerator），领域服务仅依赖该接口，
 * 避免直接依赖具体的 ID 生成工具类。
 *
 * @author daicy
 * @date 2026/3/9
 */
public interface IdGenerator {

    /**
     * 生成下一个全局唯一 ID 字符串。
     *
     * @return 全局唯一 ID
     */
    String nextId();
}

