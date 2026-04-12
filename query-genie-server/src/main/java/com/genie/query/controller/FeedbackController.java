package com.genie.query.controller;

import com.genie.query.application.FeedbackApplication;
import com.genie.query.controller.dto.FeedbackRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈控制器。
 *
 * <p>接口：POST /genie/api/agent/feedback
 *
 * @author daicy
 * @date 2026/4/12
 */
@RestController
@RequestMapping("/agent/feedback")
public class FeedbackController {

    private static final Logger log = LoggerFactory.getLogger(FeedbackController.class);

    @Autowired
    private FeedbackApplication feedbackApplication;

    /**
     * 提交用户对 Agent 答案的反馈。
     *
     * <pre>
     * POST /genie/api/agent/feedback
     * {
     *   "messageId": "123456789",
     *   "sessionId": "session-abc",
     *   "rating": 1,
     *   "comment": "非常准确"
     * }
     * </pre>
     *
     * @param request 反馈请求（rating 必填：1=好评，-1=差评；messageId 必填）
     * @return 204 No Content
     */
    @PostMapping
    public ResponseEntity<Void> submitFeedback(@RequestBody FeedbackRequest request) {
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getRating() == null || (request.getRating() != 1 && request.getRating() != -1)) {
            return ResponseEntity.badRequest().build();
        }
        log.info("[FeedbackController] 收到反馈 | messageId={} | rating={}", request.getMessageId(), request.getRating());
        feedbackApplication.handleFeedback(request);
        return ResponseEntity.noContent().build();
    }
}
