package com.genie.query.domain.agent.tool;

import com.genie.query.controller.dto.AgentAskRequest;
import com.genie.query.domain.agent.tool.spi.AgentTool;
import com.genie.query.domain.agent.tool.spi.AgentToolMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具注册表：统一管理所有 Agent 工具的注册与按条件过滤。
 *
 * <p>扩展方式：新建工具类实现 {@link AgentTool}，标注 {@link AgentToolMeta}，
 * 注册为 Spring Bean 即可，无需修改编排器。
 *
 * <p>过滤优先级：
 * <ol>
 *   <li>{@code alwaysLoad=true} → 始终加入，跳过所有后续检查</li>
 *   <li>业务条件：datasourceIds 为空列表时跳过 SQL 工具；knowledgeCodes 为空列表时跳过 KB 工具</li>
 *   <li>ToolForce：通过反射读取 {@link AgentToolMeta#toolForceField()} 对应字段，
 *       {@code Boolean.FALSE} 时强制禁用</li>
 * </ol>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** Spring 自动收集所有实现了 {@link AgentTool} 的 Bean */
    @Autowired
    private List<AgentTool> allTools;

    /**
     * 根据请求参数返回本次 Agent 执行应加载的工具集。
     *
     * @param toolForce      前端传入的工具强制约束（null 表示无约束）
     * @param datasourceIds  可用数据源 ID 列表（null=全部可用，empty=不可用）
     * @param knowledgeCodes 可用知识库编码列表（null=全部可用，empty=不可用）
     * @return 过滤后的工具实例列表
     */
    public List<Object> getTools(AgentAskRequest.ToolForce toolForce,
                                 List<Long> datasourceIds,
                                 List<String> knowledgeCodes) {
        List<Object> result = new ArrayList<>();

        for (AgentTool tool : allTools) {
            AgentToolMeta meta = tool.getClass().getAnnotation(AgentToolMeta.class);
            if (meta == null) {
                continue;
            }

            // alwaysLoad 工具始终加入，跳过所有后续检查
            if (meta.alwaysLoad()) {
                result.add(tool);
                continue;
            }

            // 业务条件：SQL 工具要求数据源非空列表
            if ("sql".equals(meta.name()) && datasourceIds != null && datasourceIds.isEmpty()) {
                log.debug("[ToolRegistry] 跳过 sql 工具：datasourceIds 为空列表");
                continue;
            }

            // 业务条件：KB 工具要求知识库编码非空列表
            if ("knowledge".equals(meta.name()) && knowledgeCodes != null && knowledgeCodes.isEmpty()) {
                log.debug("[ToolRegistry] 跳过 knowledge 工具：knowledgeCodes 为空列表");
                continue;
            }

            // ToolForce 强制禁用检查（通过反射读取 toolForceField）
            if (meta.forceable() && isForceDisabled(meta, toolForce)) {
                log.debug("[ToolRegistry] 跳过 {} 工具：被 ToolForce 强制禁用", meta.name());
                continue;
            }

            result.add(tool);
        }

        log.info("[ToolRegistry] 本次工具集: {}",
                result.stream()
                        .map(t -> t.getClass().getAnnotation(AgentToolMeta.class).name())
                        .collect(Collectors.toList()));
        return result;
    }

    /**
     * 通过反射读取 {@link AgentAskRequest.ToolForce} 中 {@code toolForceField} 对应字段的值，
     * 若为 {@code Boolean.FALSE} 则表示该工具被强制禁用。
     */
    private boolean isForceDisabled(AgentToolMeta meta, AgentAskRequest.ToolForce toolForce) {
        if (toolForce == null || meta.toolForceField().isBlank()) {
            return false;
        }
        try {
            Field field = AgentAskRequest.ToolForce.class.getDeclaredField(meta.toolForceField());
            field.setAccessible(true);
            Object value = field.get(toolForce);
            return Boolean.FALSE.equals(value);
        } catch (Exception e) {
            log.warn("[ToolRegistry] 反射读取 ToolForce.{} 失败: {}", meta.toolForceField(), e.getMessage());
            return false;
        }
    }
}
