package com.genie.query.domain.document.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Getter
public enum DocCategory {
    UNSTRUCTURED("UNSTRUCTURED"), // 非结构化文件
    STRUCTURED("STRUCTURED"); // 结构化文件

    private String value;

    private DocCategory(String value) {
        this.value = value;
    }

    @JsonValue  // 序列化时使用value字段
    public String getValue() {
        return value;
    }

    // 添加fromValue方法用于反序列化
    public static DocStatus fromValue(String value) {
        for (DocStatus type : DocStatus.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }
}
