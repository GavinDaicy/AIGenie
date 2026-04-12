package com.genie.query.domain.agent;

import com.genie.query.domain.agent.routing.DefaultSemanticRouter;
import com.genie.query.domain.agent.routing.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultSemanticRouter 历史感知路由单元测试：
 * 验证多轮对话场景下"那这个呢？"等指代词能正确传递历史上下文给 LLM 分类。
 */
@ExtendWith(MockitoExtension.class)
class DefaultSemanticRouterTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private DefaultSemanticRouter router;

    @Test
    void route_withHistory_promptShouldContainHistoryContext() {
        mockLlmResponse("DATA_QUERY");
        List<String> history = List.of(
                "用户：上个月螺纹钢的价格走势怎样？",
                "助手：上个月螺纹钢均价4180元/吨，整体呈下降趋势");

        router.route("那这个呢？", history);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String promptText = captor.getValue().getInstructions().get(0).getText();
        assertThat(promptText).contains("近期对话上下文");
        assertThat(promptText).contains("上个月螺纹钢的价格走势怎样");
        assertThat(promptText).contains("那这个呢");
    }

    @Test
    void route_withHistory_shouldReturnLlmClassificationResult() {
        mockLlmResponse("DATA_QUERY");
        List<String> history = List.of(
                "用户：钢筋最近的价格？",
                "助手：HRB400螺纹钢当前均价约4200元/吨");

        QuestionType result = router.route("那这个呢？", history);

        assertThat(result).isEqualTo(QuestionType.DATA_QUERY);
    }

    @Test
    void route_withEmptyHistory_behaviorSameAsNoHistory() {
        mockLlmResponse("KNOWLEDGE");

        QuestionType withEmpty = router.route("那这个呢？", List.of());
        QuestionType withNull  = router.route("那这个呢？", null);

        assertThat(withEmpty).isEqualTo(QuestionType.KNOWLEDGE);
        assertThat(withNull).isEqualTo(QuestionType.KNOWLEDGE);
    }

    private void mockLlmResponse(String content) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
