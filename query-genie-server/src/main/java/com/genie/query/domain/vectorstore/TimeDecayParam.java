package com.genie.query.domain.vectorstore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索侧时间衰减参数（已解析为脚本可用的形式）。
 *
 * @author daicy
 * @date 2026/3/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeDecayParam {
    /** 是否启用时间衰减 */
    private boolean enabled;
    /** 参与衰减的时间字段名（ES doc 中的字段名） */
    private String timeField;
    /** 衰减类型：LINEAR / EXPONENTIAL / GAUSSIAN */
    private String decayType;
    /** 衰减尺度（毫秒） */
    private Long scaleMillis;
    /** 衰减偏移（毫秒） */
    private Long offsetMillis;
    /** 衰减强度（(0,1]），用于推导 lambda/sigma（可选） */
    private Double decay;
    /** 时间权重下限（[0,1]） */
    private Double floor;
    /** 仅当基础分数达到阈值时应用（可选） */
    private Double applyThreshold;
    /** 指数衰减参数 lambda（1/ms，可选） */
    private Double lambda;
    /** 高斯参数 sigma（ms，可选） */
    private Double sigma;
}

