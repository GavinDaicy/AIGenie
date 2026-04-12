package com.genie.query.domain.agent.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 默认语义路由器实现：关键词规则优先，兜底 LLM 分类。
 *
 * <p>分类策略：
 * <ol>
 *   <li>关键词规则（毫秒级，零Token消耗）：数据分析关键词 → DATA_QUERY；知识文档关键词 → KNOWLEDGE</li>
 *   <li>LLM兜底分类（单次轻量调用，无需工具）：对规则未能识别的模糊问题做精准分类</li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Service
public class DefaultSemanticRouter implements SemanticRouter {

    private static final Logger log = LoggerFactory.getLogger(DefaultSemanticRouter.class);

    private static final List<Pattern> DATA_QUERY_PATTERNS = List.of(
            Pattern.compile("价格|报价|行情|单价|均价|最低价|最高价", Pattern.CASE_INSENSITIVE),
            Pattern.compile("统计|汇总|合计|总量|总数|数量|金额|总额", Pattern.CASE_INSENSITIVE),
            Pattern.compile("排名|排行|前[0-9十]+|最多|最少|最大|最小|最高|最低", Pattern.CASE_INSENSITIVE),
            Pattern.compile("对比|比较|差异|变化|趋势|增长|下降|波动", Pattern.CASE_INSENSITIVE),
            Pattern.compile("订单|采购|销售|供应商|客户|库存", Pattern.CASE_INSENSITIVE),
            Pattern.compile("近[0-9一二三四五六七八九十百]+[天周月年季]|上[周月年季]|本[周月年季]|去年|今年|今天|昨天", Pattern.CASE_INSENSITIVE),
            Pattern.compile("哪家|哪个|哪些.*(最|更|较)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> KNOWLEDGE_PATTERNS = List.of(
            Pattern.compile("什么是|是什么|什么叫|叫什么|定义|概念|含义", Pattern.CASE_INSENSITIVE),
            Pattern.compile("如何|怎么|怎样|步骤|方法|流程|操作|使用方法", Pattern.CASE_INSENSITIVE),
            Pattern.compile("规范|标准|规定|要求|规格|参数|说明书|手册", Pattern.CASE_INSENSITIVE),
            Pattern.compile("介绍|说明|解释|讲解|描述", Pattern.CASE_INSENSITIVE),
            Pattern.compile("有哪些类型|分类|种类|区别|特点|优缺点", Pattern.CASE_INSENSITIVE)
    );

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override
    public QuestionType route(String question, List<String> recentHistory) {
        if (question == null || question.isBlank()) {
            return QuestionType.KNOWLEDGE;
        }

        String trimmed = question.trim();

        // 最高优先：含多个子问题的复合问题，直接路由 COMPLEX（确保全工具可用）
        if (isMultiQuestion(trimmed)) {
            log.debug("[SemanticRouter] 检测到多子问题（含{}个问号），直接路由 COMPLEX | question={}",
                    countQuestionMarks(trimmed), trimmed);
            return QuestionType.COMPLEX;
        }

        // 优先：关键词规则（零成本，毫秒级，无需历史）
        if (matchesAny(trimmed, DATA_QUERY_PATTERNS)) {
            log.debug("[SemanticRouter] 规则命中 DATA_QUERY | question={}", trimmed);
            return QuestionType.DATA_QUERY;
        }

        if (matchesAny(trimmed, KNOWLEDGE_PATTERNS)) {
            log.debug("[SemanticRouter] 规则命中 KNOWLEDGE | question={}", trimmed);
            return QuestionType.KNOWLEDGE;
        }

        // 兜底：LLM 分类（单次轻量调用，携带历史上下文）
        return llmClassify(trimmed, recentHistory);
    }

    private boolean isMultiQuestion(String question) {
        return countQuestionMarks(question) >= 2;
    }

    private long countQuestionMarks(String question) {
        return question.chars().filter(c -> c == '？' || c == '?').count();
    }

    private boolean matchesAny(String question, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(question).find()) {
                return true;
            }
        }
        return false;
    }

    private QuestionType llmClassify(String question, List<String> recentHistory) {
        if (chatModel == null) {
            log.warn("[SemanticRouter] ChatModel 未配置，兜底返回 KNOWLEDGE");
            return QuestionType.KNOWLEDGE;
        }

        try {
            String prompt = buildClassifyPrompt(question, recentHistory);
            String response = chatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult().getOutput().getText();

            if (response == null) {
                return QuestionType.COMPLEX;
            }

            String upper = response.trim().toUpperCase();
            if (upper.contains("DATA_QUERY")) {
                log.debug("[SemanticRouter] LLM 分类 DATA_QUERY | question={}", question);
                return QuestionType.DATA_QUERY;
            } else if (upper.contains("KNOWLEDGE")) {
                log.debug("[SemanticRouter] LLM 分类 KNOWLEDGE | question={}", question);
                return QuestionType.KNOWLEDGE;
            } else {
                log.debug("[SemanticRouter] LLM 分类 COMPLEX | question={}", question);
                return QuestionType.COMPLEX;
            }
        } catch (Exception e) {
            log.warn("[SemanticRouter] LLM 分类失败，兜底 COMPLEX | error={}", e.getMessage());
            return QuestionType.COMPLEX;
        }
    }

    private String buildClassifyPrompt(String question, List<String> recentHistory) {
        String header = "请将以下用户问题分类为以下三类之一，只回复类别名称，不要解释：\n" +
                "- DATA_QUERY：数据分析、统计、查询、价格、排名、比较、趋势等需要查数据库的问题\n" +
                "- KNOWLEDGE：产品信息、规范文件、操作手册、概念解释、\"什么是\"、\"如何\"等知识文档类问题\n" +
                "- COMPLEX：需要同时查知识库和数据库、或问题复杂需要多步推理的综合类问题\n\n";

        if (recentHistory != null && !recentHistory.isEmpty()) {
            return header +
                    "【近期对话上下文（最近" + recentHistory.size() + "条）】\n" +
                    String.join("\n", recentHistory) + "\n\n" +
                    "【当前需要分类的问题】\n" + question + "\n\n" +
                    "分类结果：";
        }
        return header + "用户问题：" + question + "\n\n分类结果：";
    }
}
