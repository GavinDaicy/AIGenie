package com.genie.query.domain.etlpipeline.spliter;

import com.genie.query.domain.document.model.Document;
import com.genie.query.domain.etlpipeline.model.Chunk;
import com.genie.query.domain.knowledge.model.KLField;

import java.io.InputStream;
import java.util.List;

/**
 * 文档切块策略。
 * 单内容字段：递归切块（按段落/大小）；多字段：按 Excel/CSV 行切块。
 *
 * @author daicy
 * @date 2026/1/16
 */
public interface DocumentSplitter {

    /**
     * 根据解析后的内容与知识库字段配置进行切块。
     *
     * @param doc           文档
     * @param parsedInput   解析后的文件流（文本或 Excel/CSV）
     * @param userFields    知识库用户配置字段（不含 id、doc_id）
     * @return 切块列表
     */
    List<Chunk> split(Document doc, InputStream parsedInput, List<KLField> userFields);
}
