package com.genie.query.domain.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认上下文窗口管理器实现：工具调用次数超过阈值时，对早期 ToolResponse 做摘要压缩。
 *
 * <p>压缩策略：
 * <ol>
 *   <li>保留 SystemMessage 和最后 N 轮完整对话</li>
 *   <li>将早期 ToolResponseMessage 中的内容通过 LLM 摘要为一段简洁的结论文本</li>
 *   <li>用一条 SystemMessage（"已摘要早期工具结果"）替换被压缩的历史消息</li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class DefaultContextWindowManager implements ContextWindowManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextWindowManager.class);

    /** 工具调用次数超过此阈值时触发压缩 */
    private static final int COMPRESS_THRESHOLD = 6;

    /** 压缩后保留的最近完整轮次消息条数 */
    private static final int KEEP_RECENT_MESSAGES = 6;

    private static final String SUMMARIZE_PROMPT =
            "请将以下工具执行结果简洁地总结为关键结论（100字以内），去掉原始数据表格，只保留核心数字和结论：\n\n%s";

    @Override
    public boolean shouldCompress(AgentContext context) {
        return context.getToolCallCount() >= COMPRESS_THRESHOLD;
    }

    @Override
    public void compress(AgentContext context, ChatModel chatModel) {
        List<Message> messages = context.getMessages();
        if (messages.size() <= KEEP_RECENT_MESSAGES + 1) {
            return;
        }

        log.info("[ContextWindowManager] 触发上下文压缩 | toolCallCount={} | 消息总数={}",
                context.getToolCallCount(), messages.size());

        // 找出系统消息和需要保留的最近消息
        Message systemMsg = messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .findFirst()
                .orElse(null);

        int splitPoint = Math.max(1, messages.size() - KEEP_RECENT_MESSAGES);
        List<Message> earlyMessages = messages.subList(1, splitPoint); // 跳过 SystemMessage
        List<Message> recentMessages = messages.subList(splitPoint, messages.size());

        // 从早期消息中提取 ToolResponse 内容进行摘要
        String toolResultsText = earlyMessages.stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .map(m -> extractToolResponseContent((ToolResponseMessage) m))
                .collect(Collectors.joining("\n\n"));

        String summary;
        if (toolResultsText.isBlank()) {
            summary = "（早期工具调用结果已清理，无重要结论）";
        } else {
            summary = summarizeWithLlm(toolResultsText, chatModel);
        }

        // 构建压缩后的消息列表
        List<Message> compressed = new ArrayList<>();
        if (systemMsg != null) {
            compressed.add(systemMsg);
        }
        compressed.add(new SystemMessage("[早期工具执行摘要] " + summary));
        compressed.addAll(recentMessages);

        context.replaceMessages(compressed);
        log.info("[ContextWindowManager] 压缩完成 | 压缩后消息数={}", compressed.size());
    }

    private String extractToolResponseContent(ToolResponseMessage msg) {
        return msg.getResponses().stream()
                .map(r -> r.name() + ": " + r.responseData())
                .collect(Collectors.joining("\n"));
    }

    private String summarizeWithLlm(String toolResultsText, ChatModel chatModel) {
        try {
            String prompt = String.format(SUMMARIZE_PROMPT, toolResultsText);
            return chatModel.call(
                    new org.springframework.ai.chat.prompt.Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("[ContextWindowManager] LLM摘要失败，使用截断方案: {}", e.getMessage());
            return toolResultsText.length() > 200
                    ? toolResultsText.substring(0, 200) + "..."
                    : toolResultsText;
        }
    }
}
