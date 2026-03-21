package com.genie.query.domain.etlpipeline.spliter;

import com.genie.query.domain.document.model.DocType;
import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.etlpipeline.spliter.impl.MarkdownHeadingDocumentSplitter;
import com.genie.query.domain.etlpipeline.spliter.impl.RecursiveDocumentSplitter;
import com.genie.query.domain.etlpipeline.spliter.impl.RowBasedDocumentSplitter;
import com.genie.query.domain.knowledge.model.ChunkingPolicy;
import com.genie.query.domain.knowledge.model.ChunkingStrategy;
import com.genie.query.domain.knowledge.model.KLField;
import com.genie.query.domain.knowledge.model.Knowledge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档切块策略工厂：根据知识库分块配置、文档类型与字段配置选择合适的 DocumentSplitter 实现。
 * <p>
 * 现阶段支持：
 * - RECURSIVE_TEXT：长文本递归切块；
 * - ROW_BASED / ROW_GROUPED：按行切块（统一走 RowBasedDocumentSplitter，ROW_GROUPED 预留后续实现）；
 * - MARKDOWN_HEADING：暂时退化为 RECURSIVE_TEXT，后续由 MarkdownHeadingDocumentSplitter 接管。
 *
 * @author daicy
 * @date 2026/3/9
 */
@Component
public class DocumentSplitterFactory {

    @Autowired
    private RecursiveDocumentSplitter recursiveDocumentSplitter;
    @Autowired
    private RowBasedDocumentSplitter rowBasedDocumentSplitter;
    @Autowired
    private MarkdownHeadingDocumentSplitter markdownHeadingDocumentSplitter;

    /**
     * 根据知识库配置、文档类型与字段配置选择切块实现。
     * <p>
     * 策略优先，未配置策略时：
     * - Excel/CSV 等表格类文档：始终按行切块；
     * - 其它类型文档：回退到“单语义字段 → 递归切块；否则按行切块”的旧逻辑。
     */
    public DocumentSplitter getSplitter(Document doc, Knowledge knowledge, List<KLField> userFields) {
        ChunkingPolicy policy = knowledge != null ? knowledge.getChunkingPolicy() : null;
        ChunkingStrategy strategy = policy != null ? policy.getStrategy() : null;

        // 1. - Excel/CSV 属于天然的表格文档，应始终按行切块，避免被递归长文本策略误用；
        //    - Markdown文档使用 MarkdownHeadingDocumentSplitter 切块；
        //    - 其它类型再回退到旧逻辑。
        if (doc != null && doc.getType() != null) {
            DocType type = doc.getType();
            if (type == DocType.EXCEL || type == DocType.CSV) {
                return rowBasedDocumentSplitter;
            } else if (type == DocType.MARKDOWN) {
                return markdownHeadingDocumentSplitter;
            }
        }

        // 2. 显式配置优先：若知识库配置了分块策略，则严格按照策略选择实现
        if (strategy != null) {
            return switch (strategy) {
                case RECURSIVE_TEXT -> recursiveDocumentSplitter;
                case MARKDOWN_HEADING -> markdownHeadingDocumentSplitter;
                case ROW_BASED, ROW_GROUPED -> rowBasedDocumentSplitter;
            };
        }

        // 3. 单内容字段且可语义检索 → 递归切块
        if (userFields != null
                && userFields.size() == 1
                && Boolean.TRUE.equals(userFields.get(0).getSemanticSearchable())) {
            return recursiveDocumentSplitter;
        }

        // 4. 兜底：递归切块
        return recursiveDocumentSplitter;
    }
}

