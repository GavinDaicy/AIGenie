package com.genie.query.domain.agent.tool;

import com.genie.query.domain.qa.service.QaQueryService;
import com.genie.query.domain.query.service.QueryRewriteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagSearchTool 单元测试：覆盖改写成功/失败降级/null降级/kbCodes传递/空结果降级场景。
 */
@ExtendWith(MockitoExtension.class)
class RagSearchToolTest {

    @Mock
    private QaQueryService qaQueryService;

    @Mock
    private QueryRewriteService queryRewriteService;

    @InjectMocks
    private RagSearchTool ragSearchTool;

    private static final String QUESTION = "螺纹钢最新价格是多少？";

    @Test
    void searchKnowledge_shouldUseRewrittenQueries_whenRewriteServiceAvailable() throws Exception {
        QueryRewriteService.QueryRewriteResult rewriteResult =
                new QueryRewriteService.QueryRewriteResult("螺纹钢现价", List.of("螺纹钢今日价格", "建筑钢材最新报价"));
        when(queryRewriteService.generateQueries(eq(QUESTION), eq(3), any())).thenReturn(rewriteResult);
        when(qaQueryService.searchWithQueries(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any()))
                .thenReturn(List.of());

        ragSearchTool.searchKnowledge(QUESTION, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> queriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(qaQueryService).searchWithQueries(queriesCaptor.capture(), any(), any(), anyInt(), anyInt(), anyBoolean(), any());
        List<String> capturedQueries = queriesCaptor.getValue();
        assertThat(capturedQueries).hasSize(3);
        assertThat(capturedQueries).containsExactly("螺纹钢现价", "螺纹钢今日价格", "建筑钢材最新报价");
    }

    @Test
    void searchKnowledge_shouldFallbackToOriginalQuestion_whenRewriteServiceIsNull() throws Exception {
        RagSearchTool toolWithoutRewrite = new RagSearchTool();
        injectField(toolWithoutRewrite, "qaQueryService", qaQueryService);

        when(qaQueryService.searchWithQueries(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any()))
                .thenReturn(List.of());

        toolWithoutRewrite.searchKnowledge(QUESTION, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> queriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(qaQueryService).searchWithQueries(queriesCaptor.capture(), any(), any(), anyInt(), anyInt(), anyBoolean(), any());
        assertThat(queriesCaptor.getValue()).containsExactly(QUESTION);
    }

    @Test
    void searchKnowledge_shouldFallbackToOriginalQuestion_whenRewriteServiceThrows() throws Exception {
        when(queryRewriteService.generateQueries(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("改写服务异常"));
        when(qaQueryService.searchWithQueries(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any()))
                .thenReturn(List.of());

        ragSearchTool.searchKnowledge(QUESTION, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> queriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(qaQueryService).searchWithQueries(queriesCaptor.capture(), any(), any(), anyInt(), anyInt(), anyBoolean(), any());
        assertThat(queriesCaptor.getValue()).containsExactly(QUESTION);
    }

    @Test
    void searchKnowledge_shouldPassKbCodesToRewriteContext() throws Exception {
        QueryRewriteService.QueryRewriteResult rewriteResult =
                new QueryRewriteService.QueryRewriteResult(QUESTION, List.of());
        when(queryRewriteService.generateQueries(eq(QUESTION), eq(3), any())).thenReturn(rewriteResult);
        when(qaQueryService.searchWithQueries(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any()))
                .thenReturn(List.of());

        ragSearchTool.searchKnowledge(QUESTION, "kb001,kb002");

        ArgumentCaptor<QueryRewriteService.QueryRewriteContext> ctxCaptor =
                ArgumentCaptor.forClass(QueryRewriteService.QueryRewriteContext.class);
        verify(queryRewriteService).generateQueries(eq(QUESTION), eq(3), ctxCaptor.capture());
        QueryRewriteService.QueryRewriteContext ctx = ctxCaptor.getValue();
        assertThat(ctx.getKnowledgeCodes()).containsExactlyInAnyOrder("kb001", "kb002");
    }

    @Test
    void searchKnowledge_shouldFallbackToOriginalQuestion_whenRewriteResultIsEmpty() throws Exception {
        QueryRewriteService.QueryRewriteResult emptyResult =
                new QueryRewriteService.QueryRewriteResult("", List.of());
        when(queryRewriteService.generateQueries(eq(QUESTION), eq(3), any())).thenReturn(emptyResult);
        when(qaQueryService.searchWithQueries(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), any()))
                .thenReturn(List.of());

        ragSearchTool.searchKnowledge(QUESTION, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> queriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(qaQueryService).searchWithQueries(queriesCaptor.capture(), any(), any(), anyInt(), anyInt(), anyBoolean(), any());
        assertThat(queriesCaptor.getValue()).containsExactly(QUESTION);
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = RagSearchTool.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
