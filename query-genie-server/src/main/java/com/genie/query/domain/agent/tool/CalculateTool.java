package com.genie.query.domain.agent.tool;

import com.genie.query.domain.agent.tool.spi.AgentTool;
import com.genie.query.domain.agent.tool.spi.AgentToolMeta;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 精确数学计算工具：使用 exp4j 执行标准数学表达式，返回精确数值结果。
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>querySql 返回原始数据后，计算环比/同比增长率</li>
 *   <li>计算多步骤公式（占比、加权均值、复合增长率等）</li>
 *   <li>验证数值结论，避免 LLM 直接脑算产生精度误差</li>
 * </ul>
 *
 * <p>支持运算：四则运算、幂运算（^）、sqrt/sin/cos/tan/log/log10/abs/ceil/floor/round。
 * 除零、非法表达式均返回友好错误信息，不抛出异常。
 *
 * @author daicy
 */
@AgentToolMeta(name = "calculate", group = "builtin", alwaysLoad = true, forceable = false)
@Component
public class CalculateTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CalculateTool.class);

    @Tool(description = "执行精确数学计算，返回可验证的数值结果。" +
            "适用于：对 querySql 查到的数据做二次计算（环比增长率、占比、总金额、加权均值等多步公式）。" +
            "输入标准数学表达式字符串，支持 +/-*/^、括号、sqrt/log/abs 等函数。" +
            "示例：'(200-150)/150*100' 计算增长率；'sqrt(144)' 计算平方根。" +
            "不适用：SQL 内置聚合（SUM/AVG）、单步简单四则运算。")
    public String calculateExpression(
            @ToolParam(description = "标准数学表达式，如 '(200-150)/150*100' 或 '1000*0.13+500'") String expression) {
        if (expression == null || expression.isBlank()) {
            return "计算失败：表达式不能为空";
        }
        String trimmed = expression.trim();
        try {
            Expression expr = new ExpressionBuilder(trimmed).build();
            double result = expr.evaluate();
            if (Double.isNaN(result)) {
                return "计算失败：表达式结果为 NaN，请检查是否存在无效运算（如 0/0）";
            }
            if (Double.isInfinite(result)) {
                return "计算失败：除零错误，分母不能为 0";
            }
            String formatted = formatResult(result);
            log.debug("[CalculateTool] {} = {}", trimmed, formatted);
            return "计算结果：" + trimmed + " = " + formatted;
        } catch (ArithmeticException e) {
            log.debug("[CalculateTool] 算术异常 | expr={} | error={}", trimmed, e.getMessage());
            return "计算失败：" + e.getMessage();
        } catch (Exception e) {
            log.debug("[CalculateTool] 表达式解析失败 | expr={} | error={}", trimmed, e.getMessage());
            return "计算失败：表达式格式有误，" + e.getMessage();
        }
    }

    /**
     * 格式化结果：整数值去掉小数点，保留最多 6 位有效小数。
     */
    private String formatResult(double result) {
        if (result == Math.floor(result) && !Double.isInfinite(result) && Math.abs(result) < 1e15) {
            return String.valueOf((long) result);
        }
        String s = String.format("%.6f", result);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }
}
