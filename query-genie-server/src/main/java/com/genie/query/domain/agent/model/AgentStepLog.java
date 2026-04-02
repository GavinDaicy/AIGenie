package com.genie.query.domain.agent.model;

import lombok.Data;

import java.util.Date;

/**
 * Agent 推理步骤日志实体。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class AgentStepLog {
    private Long id;
    /** 关联会话ID（chat_session.id） */
    private String sessionId;
    /** 关联消息ID（chat_message.id），最终答案写入后关联 */
    private String messageId;
    /** ReAct 迭代轮次（从1开始） */
    private Integer iteration;
    /** 步骤类型: THOUGHT / TOOL_CALL / TOOL_RESULT / ASK_USER / FINAL_ANSWER / ERROR */
    private String stepType;
    /** 工具名称（仅 TOOL_CALL/TOOL_RESULT 时有值） */
    private String toolName;
    /** 步骤内容（思考文本 / 工具参数JSON / 工具返回结果） */
    private String content;
    /** 步骤执行耗时（毫秒） */
    private Integer durationMs;
    private Date createdAt;
}
