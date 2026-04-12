package com.genie.query.domain.agent.tool.spi;

/**
 * Agent 工具标记接口。
 *
 * <p>所有 Agent 工具类须实现此接口，{@link com.genie.query.domain.agent.tool.ToolRegistry}
 * 通过 Spring 自动注入 {@code List<AgentTool>} 收集全部工具实例。
 * 结合 {@link AgentToolMeta} 注解声明工具元数据，无需修改编排器即可动态扩展工具集。
 *
 * <p>扩展示例：
 * <pre>{@code
 * @AgentToolMeta(name = "myTool", group = "custom", forceable = true, toolForceField = "myTool")
 * @Component
 * public class MyTool implements AgentTool {
 *     @Tool(description = "...")
 *     public String doSomething(...) { ... }
 * }
 * }</pre>
 */
public interface AgentTool {
}
