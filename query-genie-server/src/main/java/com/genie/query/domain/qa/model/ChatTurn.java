package com.genie.query.domain.qa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话轮次，用于构造多轮 LLM 上下文（仅 role + content，sources 不参与）。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {
    /** user / assistant */
    private String role;
    private String content;
}
