package com.genie.query.domain.document.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/7
 */
@Getter
public enum DocType {

    TEXT("txt", "text"),
    IMAGE("jpg,png,gif,bmp", "image"),
    PDF("pdf", "pdf"),
    EXCEL("xls,xlsx", "excel"),
    CSV("csv", "csv"),
    WORD("doc,docx", "word"),
    PPT("ppt,pptx", "ppt"),
    ZIP("zip", "zip"),
    MARKDOWN("md", "markdown"),
    JSON("json", "json"),
    WEBLINK( "", "web"), // 网页链接
    YUQUELINK("", "yuque"), // 语雀链接
    UNKNOWN("", "unknown");

    private final String fileSuffix;

    DocType(String fileSuffix, String value) {
        this.fileSuffix = fileSuffix;
        this.value = value;
    }

    public static DocType getDocTypeByFileName(String name) {
        // 获取后缀
        String suffix = StringUtils.substringAfterLast(name, ".");
        if (StringUtils.isBlank(suffix)) {
            return UNKNOWN;
        }
        for (DocType value : values()) {
            List<String> list = Arrays.asList(value.fileSuffix.split(","));
            list.forEach(s -> s = s.trim());
            if (list.contains(suffix)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    private String value;

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
