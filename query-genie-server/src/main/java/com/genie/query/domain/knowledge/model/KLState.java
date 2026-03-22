package com.genie.query.domain.knowledge.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/6
 */
@Getter
public enum KLState {
    /** 未发布 */
    UNPUBLISHED(0),
    /** 发布后修改中 */
    PUBLISHED_MODIFYING(1),
    /** 已发布 */
    PUBLISHED(2);

    private Integer value;

    KLState(Integer value) {
        this.value = value;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    @JsonCreator
    public static KLState fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (KLState state : KLState.values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown knowledge state: " + value);
    }
}
