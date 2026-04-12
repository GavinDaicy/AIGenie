package com.genie.query.domain.agent.tool.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具元数据注解，标注在实现了 {@link AgentTool} 的工具类上。
 *
 * <p>{@link com.genie.query.domain.agent.tool.ToolRegistry} 在构建工具列表时读取此注解，
 * 根据 {@link #alwaysLoad()}、{@link #forceable()} 和 {@link #toolForceField()} 决定是否将工具加入本次执行列表。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AgentToolMeta {

    /** 工具唯一名称，对应前端 ToolForce 字段名，日志和调试时使用。 */
    String name();

    /** 是否默认启用（无任何 force 配置时的默认行为）。 */
    boolean defaultEnabled() default true;

    /** 是否可被 ToolForce 强制禁用；{@code false} 表示此工具不受 ToolForce 控制（如 askUser）。 */
    boolean forceable() default true;

    /** 是否始终加载，不受任何条件控制（如 askUser 追问工具）。优先级最高，覆盖 forceable。 */
    boolean alwaysLoad() default false;

    /** 工具所属功能组，用于业务条件过滤和前端展示（如 "data" / "knowledge" / "search" / "builtin"）。 */
    String group() default "default";

    /**
     * 对应 {@code AgentAskRequest.ToolForce} 中的字段名（Java 字段名，非 JSON key）。
     * ToolRegistry 通过反射读取该字段的布尔值来判断是否被强制禁用。
     * {@code alwaysLoad=true} 的工具无需设置此字段。
     */
    String toolForceField() default "";
}
