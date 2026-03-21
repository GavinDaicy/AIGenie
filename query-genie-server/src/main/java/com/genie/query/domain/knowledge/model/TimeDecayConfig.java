package com.genie.query.domain.knowledge.model;

import lombok.Data;

/**
 * 知识库时间衰减配置。
 *
 * <p>该配置用于检索/召回排序阶段，对基础相关性分数进行时间维度加权。</p>
 *
 * @author daicy
 * @date 2026/3/17
 */
@Data
public class TimeDecayConfig {
    /**
     * 知识库默认是否启用时间衰减。
     * 请求侧可通过开关覆盖该默认值。
     */
    private Boolean enabled;

    /**
     * 时间字段来源。
     */
    private TimeDecayFieldSource fieldSource;

    /**
     * 当 fieldSource = BUSINESS_FIELD 时必填，必须为 DATE 类型字段的 fieldKey。
     */
    private String fieldKey;

    /**
     * 衰减类型：线性/指数/高斯。
     */
    private TimeDecayType decayType;

    /**
     * 衰减尺度（例如 30d、7d、86400s）。
     * 前端以字符串表达，后端在下发 ES 脚本参数时再解析为毫秒。
     */
    private String scale;

    /**
     * 衰减偏移（例如 0d、3d）。在 offset 内不衰减。
     */
    private String offset;

    /**
     * 衰减强度参数（通用），通常取 (0,1]，例如 0.5。
     * 可用于半衰期/高斯等推导。
     */
    private Double decay;

    /**
     * 时间权重下限，范围 [0,1]，防止旧内容权重无限趋近于 0。
     */
    private Double floor;

    /**
     * 可选：仅当基础相关性分数达到该阈值时才应用时间衰减。
     */
    private Double applyThreshold;

    /**
     * 可选：指数衰减参数（lambda），单位 1/ms。
     * 若前端不传，可根据 halfLife 或 scale/decay 推导生成并写入 ES 脚本参数。
     */
    private Double lambda;

    /**
     * 可选：高斯衰减参数（sigma），单位 ms。
     * 若前端不传，可根据 scale/decay 推导生成并写入 ES 脚本参数。
     */
    private Double sigma;
}

