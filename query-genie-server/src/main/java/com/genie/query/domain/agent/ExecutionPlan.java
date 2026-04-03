package com.genie.query.domain.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 执行计划：描述多步骤依赖任务的执行顺序与步骤间数据流。
 *
 * <p>由 {@link PlannerService} 生成，通过 {@link #toTaskListHint()} 序列化为
 * SystemMessage 注入 ReAct 上下文，引导 LLM 按计划顺序调用工具。
 *
 * @author daicy
 * @date 2026/4/3
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionPlan {

    @JsonProperty("needsPlan")
    private boolean needsPlan;

    @JsonProperty("steps")
    private List<Step> steps;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {

        @JsonProperty("id")
        private int id;

        @JsonProperty("tool")
        private String tool;

        @JsonProperty("goal")
        private String goal;

        @JsonProperty("outputVar")
        private String outputVar;

        @JsonProperty("dependsOn")
        private List<Integer> dependsOn;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }
        public String getGoal() { return goal; }
        public void setGoal(String goal) { this.goal = goal; }
        public String getOutputVar() { return outputVar; }
        public void setOutputVar(String outputVar) { this.outputVar = outputVar; }
        public List<Integer> getDependsOn() { return dependsOn; }
        public void setDependsOn(List<Integer> dependsOn) { this.dependsOn = dependsOn; }
    }

    public boolean isNeedsPlan() { return needsPlan; }
    public void setNeedsPlan(boolean needsPlan) { this.needsPlan = needsPlan; }
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }

    /**
     * 将执行计划格式化为可注入 SystemMessage 的任务提示文本。
     *
     * @return 格式化的执行计划提示，不需要计划时返回 null
     */
    public String toTaskListHint() {
        if (!needsPlan || steps == null || steps.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(
                "【执行计划】本问题需要多步骤执行，请严格按以下步骤顺序逐一调用工具：\n\n");
        for (Step step : steps) {
            sb.append("步骤").append(step.getId())
              .append(": [").append(step.getTool()).append("] ")
              .append(step.getGoal());
            if (step.getDependsOn() != null && !step.getDependsOn().isEmpty()) {
                String deps = step.getDependsOn().stream()
                        .map(d -> "步骤" + d).collect(Collectors.joining("、"));
                sb.append("（需要").append(deps).append("的结果）");
            }
            sb.append("\n");
        }
        sb.append("\n执行要求：\n");
        sb.append("- 严格按步骤顺序执行，不得跳步或合并\n");
        sb.append("- 有依赖的步骤，必须从上一步工具结果中提取关键数据（如ID列表、项目编码等）传入下一步查询条件\n");
        sb.append("- 全部步骤完成后，综合所有工具结果输出最终答案");
        return sb.toString();
    }
}
