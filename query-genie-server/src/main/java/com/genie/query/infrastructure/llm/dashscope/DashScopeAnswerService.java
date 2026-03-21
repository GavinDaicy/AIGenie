package com.genie.query.infrastructure.llm.dashscope;

import com.genie.query.domain.qa.model.AnswerResult;
import com.genie.query.domain.qa.model.ChatTurn;
import com.genie.query.domain.qa.service.AnswerPromptStrategy;
import com.genie.query.domain.qa.service.AnswerService;
import com.genie.query.domain.qa.service.StreamCallback;
import com.genie.query.domain.query.model.QueryResultEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 DashScope ChatModel 的答案生成实现。
 * 将检索结果格式化为上下文，构建 prompt 后调用大模型生成答案。
 *
 * @author daicy
 * @date 2026/3/8
 */
@Service
public class DashScopeAnswerService implements AnswerService {

    private static final Logger log = LoggerFactory.getLogger(DashScopeAnswerService.class);

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired
    private AnswerPromptStrategy answerPromptStrategy;

    @Override
    public AnswerResult generate(String question, List<QueryResultEntry> contextEntries) {
        return generate(question, contextEntries, List.of());
    }

    @Override
    public AnswerResult generate(String question, List<QueryResultEntry> contextEntries, List<ChatTurn> history) {
        if (chatModel == null) {
            throw new IllegalStateException("未配置 ChatModel，请在 application.yml 中配置 spring.ai.dashscope.chat 以启用智能问答");
        }
        if (contextEntries == null || contextEntries.isEmpty()) {
            return AnswerResult.builder()
                    .answer(answerPromptStrategy.emptyContextAnswer())
                    .sources(List.of())
                    .build();
        }

        String currentUserContent = answerPromptStrategy.buildUserPrompt(question, contextEntries);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(answerPromptStrategy.systemPrompt()));
        if (history != null) {
            for (ChatTurn turn : history) {
                if ("user".equalsIgnoreCase(turn.getRole())) {
                    messages.add(new UserMessage(turn.getContent()));
                } else if ("assistant".equalsIgnoreCase(turn.getRole())) {
                    messages.add(new AssistantMessage(turn.getContent()));
                }
            }
        }
        messages.add(new UserMessage(currentUserContent));

        Prompt prompt = new Prompt(messages);

        String answer;
        try {
            answer = chatModel.call(prompt).getResult().getOutput().getText();
            if (StringUtils.isBlank(answer)) {
                answer = "未能生成有效答案，请稍后重试。";
            }
        } catch (Exception e) {
            log.warn("DashScope 问答调用异常: {}", e.getMessage());
            throw new RuntimeException("大模型生成答案失败: " + e.getMessage(), e);
        }

        return AnswerResult.builder()
                .answer(answer)
                .sources(contextEntries)
                .build();
    }

    @Override
    public void streamGenerate(String question, List<QueryResultEntry> contextEntries,
                               List<ChatTurn> history, StreamCallback callback) {
        if (chatModel == null) {
            callback.onError(new IllegalStateException("未配置 ChatModel，请在 application.yml 中配置 spring.ai.dashscope.chat 以启用智能问答"));
            return;
        }
        if (contextEntries == null || contextEntries.isEmpty()) {
            String fallback = answerPromptStrategy.emptyContextAnswer();
            callback.onChunk(fallback);
            callback.onComplete(fallback, List.of());
            return;
        }

        String currentUserContent = answerPromptStrategy.buildUserPrompt(question, contextEntries);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(answerPromptStrategy.systemPrompt()));
        if (history != null) {
            for (ChatTurn turn : history) {
                if ("user".equalsIgnoreCase(turn.getRole())) {
                    messages.add(new UserMessage(turn.getContent()));
                } else if ("assistant".equalsIgnoreCase(turn.getRole())) {
                    messages.add(new AssistantMessage(turn.getContent()));
                }
            }
        }
        messages.add(new UserMessage(currentUserContent));
        Prompt prompt = new Prompt(messages);

        if (chatModel instanceof StreamingChatModel) {
            try {
                Flux<ChatResponse> flux = ((StreamingChatModel) chatModel).stream(prompt);
                AtomicReference<StringBuilder> fullText = new AtomicReference<>(new StringBuilder());
                flux.doOnNext(response -> {
                    String text = extractChunkText(response);
                    if (text != null) {
                        fullText.get().append(text);
                        if (!text.isEmpty()) {
                            callback.onChunk(text);
                        }
                    }
                })
                .doOnComplete(() -> callback.onComplete(fullText.get().toString(), contextEntries))
                .doOnError(callback::onError)
                .blockLast();
            } catch (Exception e) {
                log.warn("DashScope 流式问答异常: {}", e.getMessage());
                callback.onError(e);
            }
        } else {
            try {
                AnswerResult result = generate(question, contextEntries, history);
                String answer = result.getAnswer();
                if (StringUtils.isNotBlank(answer)) {
                    callback.onChunk(answer);
                }
                callback.onComplete(answer != null ? answer : "", contextEntries);
            } catch (Exception e) {
                callback.onError(e);
            }
        }
    }

    private String extractChunkText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        try {
            String text = response.getResult().getOutput().getText();
            return text != null ? text : "";
        } catch (Exception e) {
            return null;
        }
    }

}
