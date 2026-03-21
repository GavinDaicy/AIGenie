package com.genie.query.infrastructure.vectorstore.es;

/**
 * TODO
 *
 * @author daicy
 * @date 2026/1/9
 */
import co.elastic.clients.elasticsearch._types.mapping.*;
import com.genie.query.domain.knowledge.model.KLField;

import java.util.*;

public class KLFieldMappingBuilder {

    private static final String IK_MAX_WORD = "ik_max_word";
    private static final String IK_SMART = "ik_smart";
    private static final String STANDARD = "standard";
    public static final String DOC_UPDATE_TIME_FIELD = "doc_update_time";

    /**
     * 根据KLField列表构建Elasticsearch映射属性
     */
    public static Map<String, Property> buildProperties(List<KLField> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Property> properties = new LinkedHashMap<>();

        // 系统字段：文档更新时间（用于时间衰减排序）
        properties.put(DOC_UPDATE_TIME_FIELD, Property.of(p -> p
                .date(d -> d
                        .format("strict_date_optional_time||epoch_millis||yyyy-MM-dd HH:mm:ss")
                        .docValues(true))));

        for (KLField field : fields) {
            if (field.getFieldKey() == null || field.getType() == null) {
                continue;
            }

            Property property = buildProperty(field);
            if (property != null) {
                properties.put(field.getFieldKey(), property);
                if (field.getSemanticSearchable()) {
                    // 支持语义搜索，添加一个向量字段，支持向量搜索
                    properties.put(field.getFieldKey() + "_vector_system", buildVectorProperty(field));
                }
            }
        }

        return properties;
    }

    /**
     * 根据单个字段配置构建Property
     */
    private static Property buildProperty(KLField field) {
        return switch (field.getType()) {
            case STRING -> buildStringProperty(field);
            case INTEGER -> buildIntegerProperty(field);
            case FLOAT -> buildFloatProperty(field);
            case DOUBLE -> buildDoubleProperty(field);
            case DATE -> buildDateProperty(field);
            default -> null;
        };
    }

    /**
     * 构建向量类型属性
     */
    private static Property buildVectorProperty(KLField field) {
        return Property.of(p -> p
                .denseVector(v -> {
                    v.dims(768);
                    v.index(field.getSemanticSearchable());
                    v.similarity(DenseVectorSimilarity.Cosine);
                    return v;
                }));
    }

    /**
     * 构建字符串类型属性
     */
    private static Property buildStringProperty(KLField field) {
        Boolean fullTextSearchable = field.getFullTextSearchable();
        Boolean matchable = field.getMatchable();
        Boolean sortable = field.getSortable();

        // 如果既需要全文检索又需要精确匹配
        if (Boolean.TRUE.equals(fullTextSearchable) && Boolean.TRUE.equals(matchable)) {
            return Property.of(p -> p
                    .text(t -> {
                        t.analyzer(IK_SMART)  // 使用中文分词器
                                .searchAnalyzer(IK_SMART);

                        // 如果需要排序，添加keyword子字段
                        if (Boolean.TRUE.equals(sortable)) {
                            t.fields("keyword", Property.of(f -> f
                                    .keyword(k -> k
                                            .ignoreAbove(256)
                                            .docValues(true)
                                    )
                            ));
                        } else {
                            t.fields("keyword", Property.of(f -> f
                                    .keyword(k -> k.ignoreAbove(256))
                            ));
                        }

                        return t;
                    })
            );
        }
        // 只需要精确匹配（keyword类型）
        else if (Boolean.TRUE.equals(matchable)) {
            return Property.of(p -> p
                    .keyword(k -> {
                        k.ignoreAbove(256);

                        // 如果需要排序，启用docValues
                        if (Boolean.TRUE.equals(sortable)) {
                            k.docValues(true);
                        }

                        return k;
                    })
            );
        }
        // 只需要全文检索（text类型，不添加keyword子字段）
        else if (Boolean.TRUE.equals(fullTextSearchable)) {
            return Property.of(p -> p
                    .text(t -> t
                            .analyzer(IK_SMART)
                            .searchAnalyzer(IK_SMART)
                            // text字段默认不支持排序，除非启用fielddata
                            .fielddata(Boolean.TRUE.equals(sortable))
                    )
            );
        }
        // 既不需要全文检索也不需要精确匹配（只存储）
        else {
            return Property.of(p -> p
                    .text(t -> t
                            .index(false)      // 不可搜索
                            .store(true)       // 存储原始值
                    )
            );
        }
    }

    /**
     * 构建整数类型属性
     */
    private static Property buildIntegerProperty(KLField field) {
        return Property.of(p -> p
                .integer(i -> {
                    // 如果需要精确匹配（对于数值类型，index就是可搜索）
                    i.index(Boolean.TRUE.equals(field.getMatchable()));
                    // 如果需要排序
                    if (Boolean.TRUE.equals(field.getSortable())) {
                        i.docValues(true);
                    }
                    return i;
                })
        );
    }

    /**
     * 构建浮点数类型属性
     */
    private static Property buildFloatProperty(KLField field) {
        return Property.of(p -> p
                .float_(f -> {
                    f.index(true);
                    if (!Boolean.TRUE.equals(field.getMatchable())) {
                        f.index(false);
                    }
                    if (Boolean.TRUE.equals(field.getSortable())) {
                        f.docValues(true);
                    }
                    return f;
                })
        );
    }

    /**
     * 构建双精度类型属性
     */
    private static Property buildDoubleProperty(KLField field) {
        return Property.of(p -> p
                .double_(d -> {
                    d.index(true);
                    if (!Boolean.TRUE.equals(field.getMatchable())) {
                        d.index(false);
                    }
                    if (Boolean.TRUE.equals(field.getSortable())) {
                        d.docValues(true);
                    }
                    return d;
                })
        );
    }

    /**
     * 构建日期类型属性
     */
    private static Property buildDateProperty(KLField field) {
        return Property.of(p -> p
                .date(d -> {
                    // 支持多种日期格式
                    d.format("strict_date_optional_time||epoch_millis||yyyy-MM-dd HH:mm:ss");
                    d.index(Boolean.TRUE.equals(field.getMatchable()));
                    if (Boolean.TRUE.equals(field.getSortable())) {
                        d.docValues(true);
                    }
                    return d;
                })
        );
    }
}