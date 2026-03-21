package com.genie.query.controller;

import com.genie.query.application.QueryApplication;
import com.genie.query.controller.dto.QueryRequest;
import com.genie.query.controller.dto.QueryResultItem;
import com.genie.query.domain.vectorstore.SearchMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 检索查询控制器：支持多知识库、关键字/向量/混合检索，返回分块内容及文档信息。
 *
 * @author daicy
 * @date 2026/2/8
 */
@RestController
@RequestMapping("/query")
public class QueryController {

    @Autowired
    private QueryApplication queryApplication;

    /**
     * 检索接口。
     * <ul>
     *   <li>query：必填，查询关键词或问句</li>
     *   <li>knowledgeCodes：可选，知识库编码列表，不传则查询所有知识库</li>
     *   <li>mode：可选，KEYWORD / VECTOR / HYBRID，默认 HYBRID</li>
     *   <li>size：可选，返回条数，默认 10</li>
     * </ul>
     * 返回每条包含：分块具体内容（chunkContent）、文档信息（document）、分数（score）、知识库编码（knowledgeCode）。
     */
    @PostMapping("/search")
    public List<QueryResultItem> search(@RequestBody QueryRequest request) throws IOException {
        return queryApplication.search(request);
    }

    /**
     * 检索接口（GET，便于调试）：query 必填，knowledgeCodes 逗号分隔，mode=KEYWORD|VECTOR|HYBRID，size 默认 10。
     */
    @GetMapping("/search")
    public List<QueryResultItem> searchGet(
            @RequestParam String query,
            @RequestParam(required = false) List<String> knowledgeCodes,
            @RequestParam(required = false) SearchMode mode,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Boolean normalizeScore) throws IOException {
        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setKnowledgeCodes(knowledgeCodes);
        request.setMode(mode != null ? mode : SearchMode.HYBRID);
        request.setSize(size != null ? size : 10);
        request.setNormalizeScore(normalizeScore);
        return queryApplication.search(request);
    }
}
