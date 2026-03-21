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
public enum DocStatus {
    UPLOADING("uploading"),  // 上传中
    TOPARSE("toParse"),    // 待解析
    PARSING("parsing"),    // 解析中
    PARSED("parsed"),     // 解析完成
    FAILED("failed");     // 解析失败

    private String value;

    DocStatus(String value) {
        this.value = value;
    }

    @JsonValue  // 序列化时使用value字段
    public String getValue() {
        return value;
    }

    // 添加fromValue方法用于反序列化
    public static DocStatus fromValue(String value) {
        for (DocStatus type : DocStatus.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }
}
