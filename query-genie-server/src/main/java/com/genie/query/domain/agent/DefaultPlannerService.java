package com.genie.query.domain.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 默认任务规划器：通过单次 LLM 调用，智能判断用户问题是否含步骤依赖并生成结构化执行计划。
 *
 * <p>判断策略：
 * <ol>
 *   <li>LLM 分析问题是否存在"先获取中间数据，再基于该数据进一步查询"的依赖链</li>
 *   <li>若需要计划：输出 JSON 步骤列表（含依赖关系与输出变量名）</li>
 *   <li>若不需要计划：输出 {needsPlan: false}，调用方返回 null 降级处理</li>
 * </ol>
 *
 * <p>任何解析或调用异常均静默降级（返回 null），不影响后续 ReAct 执行。
 *
 * @author daicy
 * @date 2026/4/3
 */
@Service
public class DefaultPlannerService implements PlannerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlannerService.class);

    private static final String PLAN_PROMPT =
            "你是一个任务规划助手。分析用户问题，判断是否需要生成多步骤执行计划。\n\n" +
            "【需要生成执行计划的情况】：问题需要先获取某些中间数据，再基于这些数据进一步查询，即步骤间存在数据依赖。例如：\n" +
            "- \"查询河北市政项目的某某材料报价\"（步骤1：查项目列表取编码 → 步骤2：用编码查材料报价）\n" +
            "- \"找出销量最高的供应商的联系方式\"（步骤1：查销量最高的供应商ID → 步骤2：用ID查联系方式）\n" +
            "- \"最近采购量最多的材料，它的合格供应商有哪些\"（步骤1：查采购量最多的材料名称 → 步骤2：查该材料合格供应商）\n" +
            "- \"上个季度业绩最好的区域经理负责的项目清单\"（步骤1：查业绩最好的区域经理ID → 步骤2：查该经理的项目）\n\n" +
            "【不需要执行计划的情况】：\n" +
            "- 简单的一步查询（一条 SQL 或一次检索即可完成）\n" +
            "- 并列独立的多个问题（互不依赖，如\"总文档数？哪个知识库最多？\"）\n" +
            "- 知识问答（从知识库检索文档即可）\n" +
            "- 直接统计、排名、汇总类问题（无需中间结果转化）\n\n" +
            "可用工具：querySql（数据库查询/统计/报价），searchKnowledge（知识库文档检索），searchWeb（互联网实时搜索）\n\n" +
            "严格只输出 JSON，不要任何解释，不要 markdown 代码块：\n" +
            "不需要计划时：{\"needsPlan\":false}\n" +
            "需要计划时：\n" +
            "{\"needsPlan\":true,\"steps\":[\n" +
            "  {\"id\":1,\"tool\":\"querySql\",\"goal\":\"查询河北地区市政类型的项目列表，记录每个项目的项目编码\",\"outputVar\":\"project_codes\",\"dependsOn\":[]},\n" +
            "  {\"id\":2,\"tool\":\"querySql\",\"goal\":\"根据步骤1得到的项目编码列表（project_codes），查询指定材料的报价信息\",\"outputVar\":\"material_prices\",\"dependsOn\":[1]}\n" +
            "]}\n\n" +
            "用户问题：%s";

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ExecutionPlan plan(String question, List<String> knowledgeCodes, List<Long> datasourceIds) {
        if (chatModel == null) {
            log.warn("[PlannerService] ChatModel 未配置，跳过规划");
            return null;
        }

        try {
            String promptText = String.format(PLAN_PROMPT, question);
            String response = chatModel.call(new Prompt(new UserMessage(promptText)))
                    .getResult().getOutput().getText();

            if (response == null || response.isBlank()) {
                log.debug("[PlannerService] LLM 返回空响应，跳过规划");
                return null;
            }

            String json = extractJson(response);
            ExecutionPlan plan = objectMapper.readValue(json, ExecutionPlan.class);

            if (!plan.isNeedsPlan() || plan.getSteps() == null || plan.getSteps().isEmpty()) {
                log.debug("[PlannerService] 判断无需执行计划 | question={}", question);
                return null;
            }

            log.info("[PlannerService] 生成执行计划 | steps={} | question={}", plan.getSteps().size(), question);
            return plan;

        } catch (Exception e) {
            log.warn("[PlannerService] 规划失败，降级跳过 | question={} | error={}", question, e.getMessage());
            return null;
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }
}
