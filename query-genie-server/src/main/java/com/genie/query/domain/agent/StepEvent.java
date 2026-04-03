package com.genie.query.domain.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Agent 推理步骤事件：通过 SSE 实时推送给前端。
 *
 * <p>每个事件包含步骤类型、迭代编号、工具名称、内容文本等字段。
 * JSON序列化时忽略 null 字段，减少传输体积。
 *
 * <p>SSE payload 示例：
 * <pre>
 * {"type":"THOUGHT","iteration":1,"content":"用户询问价格比较，应查询结构化数据库"}
 * {"type":"TOOL_CALL","iteration":1,"toolName":"sql_query","params":"{...}"}
 * {"type":"TOOL_RESULT","iteration":1,"toolName":"sql_query","content":"| 供应商 | 价格 |...","durationMs":450}
 * {"type":"FINAL_ANSWER","content":"根据过去半年数据..."}
 * </pre>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepEvent {

    public enum Type {
        ROUTING,
        PLANNING,
        THINKING,
        THOUGHT_CHUNK,
        THOUGHT,
        TOOL_CALL,
        TOOL_RESULT,
        ASK_USER,
        FINAL_ANSWER,
        ERROR
    }

    private Type type;
    private Integer iteration;
    private String toolName;
    private String content;
    private String params;
    private Long durationMs;
    private Long timestamp;

    public static StepEvent routing(String questionType, String label) {
        StepEvent e = new StepEvent();
        e.type = Type.ROUTING;
        e.content = label;
        e.params = questionType;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent planning(String content) {
        StepEvent e = new StepEvent();
        e.type = Type.PLANNING;
        e.content = content;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent thinking(int iteration) {
        StepEvent e = new StepEvent();
        e.type = Type.THINKING;
        e.iteration = iteration;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent thoughtChunk(int iteration, String chunk) {
        StepEvent e = new StepEvent();
        e.type = Type.THOUGHT_CHUNK;
        e.iteration = iteration;
        e.content = chunk;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent thought(int iteration, String content) {
        StepEvent e = new StepEvent();
        e.type = Type.THOUGHT;
        e.iteration = iteration;
        e.content = content;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent toolCall(int iteration, String toolName, String params) {
        StepEvent e = new StepEvent();
        e.type = Type.TOOL_CALL;
        e.iteration = iteration;
        e.toolName = toolName;
        e.params = params;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent toolResult(int iteration, String toolName, String content, long durationMs) {
        StepEvent e = new StepEvent();
        e.type = Type.TOOL_RESULT;
        e.iteration = iteration;
        e.toolName = toolName;
        e.content = content;
        e.durationMs = durationMs;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent finalAnswer(String content) {
        StepEvent e = new StepEvent();
        e.type = Type.FINAL_ANSWER;
        e.content = content;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent askUser(String content) {
        StepEvent e = new StepEvent();
        e.type = Type.ASK_USER;
        e.content = content;
        e.timestamp = System.currentTimeMillis();
        return e;
    }

    public static StepEvent error(String message) {
        StepEvent e = new StepEvent();
        e.type = Type.ERROR;
        e.content = message;
        e.timestamp = System.currentTimeMillis();
        return e;
    }
}
