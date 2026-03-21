package com.genie.query.domain.knowledge.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/6
 */
@Getter
public enum KLFieldType {
    STRING("string"),
    INTEGER("integer"),
    FLOAT("float"),
    DOUBLE("double"),
    DATE("date");

    private String value;
    KLFieldType(String value) {
        this.value = value;
    }

    @JsonValue  // 序列化时使用value字段
    public String getValue() {
        return value;
    }

    // 添加fromValue方法用于反序列化
    public static KLFieldType fromValue(String value) {
        for (KLFieldType type : KLFieldType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }
}
