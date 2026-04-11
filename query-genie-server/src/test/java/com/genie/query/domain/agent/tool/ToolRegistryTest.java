package com.genie.query.domain.agent.tool;

import com.genie.query.controller.dto.AgentAskRequest;
import com.genie.query.domain.agent.tool.spi.AgentTool;
import com.genie.query.domain.agent.tool.spi.AgentToolMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolRegistry 单元测试，覆盖 6 个过滤场景。
 */
class ToolRegistryTest {

    // ──────────────────────────────────────────────
    // 测试用工具桩类（内部静态类，带不同的 @AgentToolMeta）
    // ──────────────────────────────────────────────

    @AgentToolMeta(name = "askUser", group = "builtin", alwaysLoad = true, forceable = false)
    static class AlwaysLoadTool implements AgentTool {}

    @AgentToolMeta(name = "sql", group = "data", forceable = true, toolForceField = "sql")
    static class SqlTool implements AgentTool {}

    @AgentToolMeta(name = "knowledge", group = "knowledge", forceable = true, toolForceField = "knowledge")
    static class KbTool implements AgentTool {}

    @AgentToolMeta(name = "webSearch", group = "search", forceable = true, toolForceField = "webSearch")
    static class WebTool implements AgentTool {}

    // ──────────────────────────────────────────────
    // 被测对象
    // ──────────────────────────────────────────────

    private ToolRegistry registry;

    private final AlwaysLoadTool alwaysLoadTool = new AlwaysLoadTool();
    private final SqlTool sqlTool = new SqlTool();
    private final KbTool kbTool = new KbTool();
    private final WebTool webTool = new WebTool();

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        ReflectionTestUtils.setField(registry, "allTools",
                List.of(alwaysLoadTool, sqlTool, kbTool, webTool));
    }

    // ──────────────────────────────────────────────
    // 场景 1：alwaysLoad 工具不受任何条件限制，始终返回
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景1: alwaysLoad=true 的工具始终加入，即使 toolForce 明确禁用也不影响")
    void alwaysLoadToolIsAlwaysIncluded() {
        // 构造一个"禁用一切 forceable 工具"的 ToolForce
        AgentAskRequest.ToolForce toolForce = new AgentAskRequest.ToolForce();
        toolForce.setSql(false);
        toolForce.setKnowledge(false);
        toolForce.setWebSearch(false);

        List<Object> tools = registry.getTools(toolForce, List.of(1L), List.of("kb1"));

        assertThat(tools).contains(alwaysLoadTool);
    }

    // ──────────────────────────────────────────────
    // 场景 2：forceable 工具被 ToolForce 明确禁用（sql=false）时不返回
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景2: sql=false 时 SQL 工具被强制排除")
    void forceableToolExcludedWhenForceDisabled() {
        AgentAskRequest.ToolForce toolForce = new AgentAskRequest.ToolForce();
        toolForce.setSql(false);

        List<Object> tools = registry.getTools(toolForce, List.of(1L), List.of("kb1"));

        assertThat(tools).doesNotContain(sqlTool);
        // 其他工具不受影响
        assertThat(tools).contains(alwaysLoadTool, kbTool, webTool);
    }

    // ──────────────────────────────────────────────
    // 场景 3：datasourceIds 为空 List 时 SQL 工具不返回
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景3: datasourceIds 为空列表时 SQL 工具被业务条件排除")
    void sqlToolExcludedWhenDatasourceIdsEmpty() {
        List<Object> tools = registry.getTools(null, List.of(), List.of("kb1"));

        assertThat(tools).doesNotContain(sqlTool);
        assertThat(tools).contains(alwaysLoadTool, kbTool, webTool);
    }

    // ──────────────────────────────────────────────
    // 场景 4：knowledgeCodes 为空 List 时 KB 工具不返回
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景4: knowledgeCodes 为空列表时 KB 工具被业务条件排除")
    void kbToolExcludedWhenKnowledgeCodesEmpty() {
        List<Object> tools = registry.getTools(null, List.of(1L), List.of());

        assertThat(tools).doesNotContain(kbTool);
        assertThat(tools).contains(alwaysLoadTool, sqlTool, webTool);
    }

    // ──────────────────────────────────────────────
    // 场景 5：toolForce 为 null 时所有工具正常返回（无约束）
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景5: toolForce=null 时所有工具均正常返回")
    void allToolsReturnedWhenToolForceIsNull() {
        List<Object> tools = registry.getTools(null, List.of(1L), List.of("kb1"));

        assertThat(tools).containsExactlyInAnyOrder(alwaysLoadTool, sqlTool, kbTool, webTool);
    }

    // ──────────────────────────────────────────────
    // 场景 6：toolForce 存在但某字段为 null（未设置）时，对应工具正常返回
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("场景6: toolForce.sql=null（未设置）时 SQL 工具仍正常返回")
    void toolReturnedWhenForceFieldIsNull() {
        AgentAskRequest.ToolForce toolForce = new AgentAskRequest.ToolForce();
        // 只设置 webSearch=false，sql 和 knowledge 保持 null
        toolForce.setWebSearch(false);

        List<Object> tools = registry.getTools(toolForce, List.of(1L), List.of("kb1"));

        // sql 和 knowledge 未被强制，应正常包含
        assertThat(tools).contains(sqlTool, kbTool, alwaysLoadTool);
        // webSearch 被禁用
        assertThat(tools).doesNotContain(webTool);
    }
}
