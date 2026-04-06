package com.genie.query.domain.agent;

import com.genie.query.domain.agent.routing.DefaultSemanticRouter;
import com.genie.query.domain.agent.routing.QuestionType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SemanticRouter 单元测试：覆盖关键词规则命中和 LLM 兜底分类。
 *
 * @author daicy
 * @date 2026/4/2
 */
@ExtendWith(MockitoExtension.class)
class SemanticRouterTest {

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private DefaultSemanticRouter router;

    // ---- DATA_QUERY 关键词规则命中 ----

    @Test
    void route_shouldReturnDataQuery_forPriceQuestion() {
        assertThat(router.route("近半年直径20钢筋哪家供应商价格最低")).isEqualTo(QuestionType.DATA_QUERY);
    }

    @Test
    void route_shouldReturnDataQuery_forStatisticsQuestion() {
        assertThat(router.route("统计上月各供应商的采购总金额")).isEqualTo(QuestionType.DATA_QUERY);
    }

    @Test
    void route_shouldReturnDataQuery_forRankingQuestion() {
        assertThat(router.route("前10名销量最高的产品是哪些")).isEqualTo(QuestionType.DATA_QUERY);
    }

    @Test
    void route_shouldReturnDataQuery_forTrendQuestion() {
        assertThat(router.route("近三个月钢材价格变化趋势")).isEqualTo(QuestionType.DATA_QUERY);
    }

    @Test
    void route_shouldReturnDataQuery_forOrderQuestion() {
        assertThat(router.route("本月订单总数是多少")).isEqualTo(QuestionType.DATA_QUERY);
    }

    // ---- KNOWLEDGE 关键词规则命中 ----

    @Test
    void route_shouldReturnKnowledge_forWhatIsQuestion() {
        assertThat(router.route("什么是HRB400钢筋")).isEqualTo(QuestionType.KNOWLEDGE);
    }

    @Test
    void route_shouldReturnKnowledge_forHowToQuestion() {
        assertThat(router.route("如何使用钢筋检测仪")).isEqualTo(QuestionType.KNOWLEDGE);
    }

    @Test
    void route_shouldReturnKnowledge_forSpecificationQuestion() {
        assertThat(router.route("钢筋的国家规范标准有哪些")).isEqualTo(QuestionType.KNOWLEDGE);
    }

    @Test
    void route_shouldReturnKnowledge_forIntroductionQuestion() {
        assertThat(router.route("介绍一下HRB500和HRB400的区别")).isEqualTo(QuestionType.KNOWLEDGE);
    }

    // ---- LLM 兜底分类 ----

    @Test
    void route_shouldCallLlm_whenNoKeywordMatches() {
        mockLlmResponse("DATA_QUERY");
        QuestionType result = router.route("帮我看看这个情况");
        assertThat(result).isEqualTo(QuestionType.DATA_QUERY);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void route_shouldReturnComplex_whenLlmRespondsComplex() {
        mockLlmResponse("COMPLEX");
        assertThat(router.route("综合分析一下最近的市场情况")).isEqualTo(QuestionType.COMPLEX);
    }

    @Test
    void route_shouldReturnComplex_whenLlmFails() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM timeout"));
        assertThat(router.route("这是一个模糊的问题")).isEqualTo(QuestionType.COMPLEX);
    }

    @Test
    void route_shouldReturnKnowledge_forBlankQuestion() {
        assertThat(router.route("  ")).isEqualTo(QuestionType.KNOWLEDGE);
        verifyNoInteractions(chatModel);
    }

    @Test
    void route_shouldReturnKnowledge_forNullQuestion() {
        assertThat(router.route(null)).isEqualTo(QuestionType.KNOWLEDGE);
        verifyNoInteractions(chatModel);
    }

    private void mockLlmResponse(String content) {
        ChatResponse chatResponse = new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage(content))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
    }
}
