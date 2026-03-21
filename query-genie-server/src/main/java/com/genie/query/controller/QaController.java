package com.genie.query.controller;

import com.genie.query.application.QaApplication;
import com.genie.query.application.SessionApplication;
import com.genie.query.controller.dto.CreateSessionResponse;
import com.genie.query.controller.dto.QaRequest;
import com.genie.query.controller.dto.QaResponse;
import com.genie.query.controller.dto.SessionDetail;
import com.genie.query.controller.dto.SessionListItem;
import com.genie.query.infrastructure.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * 智能问答控制器：基于知识库的 RAG 问答接口及会话 CRUD。
 *
 * @author daicy
 * @date 2026/3/8
 */
@RestController
@RequestMapping("/qa")
public class QaController {

    @Autowired
    private QaApplication qaApplication;
    @Autowired
    private SessionApplication sessionApplication;

    /**
     * 智能问答接口（一次性返回）。
     * <ul>
     *   <li>sessionId：可选，多轮会话 ID，不传则单轮不落库</li>
     *   <li>question：必填，用户问题</li>
     *   <li>knowledgeCodes：可选，知识库编码列表</li>
     *   <li>mode：可选，检索模式，默认 HYBRID</li>
     *   <li>size：可选，检索条数</li>
     *   <li>rerank：可选，是否对检索结果重排</li>
     * </ul>
     * 返回 answer、sources；多轮时还返回 sessionId、messageId。
     */
    @PostMapping("/ask")
    public QaResponse ask(@RequestBody QaRequest request) {
        if (request == null || StringUtils.isBlank(request.getQuestion())) {
            throw new BusinessException("问题不能为空");
        }
        try {
            return qaApplication.ask(request);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException("问答失败: " + e.getMessage());
        }
    }

    /**
     * 智能问答接口（流式输出）。
     * 入参与 /ask 一致，响应为 SSE 流：事件 chunk（正文片段）、sources（引用来源）、done（结束）、error（错误）。
     */
    @PostMapping("/ask/stream")
    public ResponseEntity<SseEmitter> askStream(@RequestBody QaRequest request) throws IOException {
        if (request == null || StringUtils.isBlank(request.getQuestion())) {
            throw new BusinessException("问题不能为空");
        }
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        try {
            qaApplication.askStream(request, emitter);
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            try {
                emitter.send(SseEmitter.event().name("error").data("问答失败: " + e.getMessage()));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        }
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);
    }

    /** 新建会话，返回 sessionId 与 title。 */
    @PostMapping("/sessions")
    public CreateSessionResponse createSession(@RequestBody(required = false) CreateSessionRequest body) {
        String knowledgeCodes = body != null && body.getKnowledgeCodes() != null
                ? String.join(",", body.getKnowledgeCodes())
                : null;
        return sessionApplication.createSession(knowledgeCodes);
    }

    /** 会话列表，按更新时间倒序。 */
    @GetMapping("/sessions")
    public List<SessionListItem> listSessions(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return sessionApplication.listSessions(offset, limit);
    }

    /** 会话详情（含消息列表）。 */
    @GetMapping("/sessions/{id}")
    public SessionDetail getSession(@PathVariable String id) {
        return sessionApplication.getSessionDetail(id);
    }

    /** 删除会话。 */
    @DeleteMapping("/sessions/{id}")
    public void deleteSession(@PathVariable String id) {
        sessionApplication.deleteSession(id);
    }

    /** 新建会话请求体（可选知识库）。 */
    @lombok.Data
    public static class CreateSessionRequest {
        private java.util.List<String> knowledgeCodes;
    }
}
