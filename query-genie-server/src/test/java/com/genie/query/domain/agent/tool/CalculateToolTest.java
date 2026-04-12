package com.genie.query.domain.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CalculateTool 单元测试：覆盖正常计算、内置函数、边界条件和错误处理。
 */
@DisplayName("CalculateTool 单元测试")
class CalculateToolTest {

    private CalculateTool tool;

    @BeforeEach
    void setUp() {
        tool = new CalculateTool();
    }

    @Test
    @DisplayName("环比增长率计算")
    void calcGrowthRate() {
        String result = tool.calculateExpression("(200-150)/150*100");
        assertThat(result).startsWith("计算结果：");
        assertThat(result).contains("33.333333");
    }

    @Test
    @DisplayName("整数结果不含小数点")
    void calcIntegerResult() {
        String result = tool.calculateExpression("sqrt(144)");
        assertThat(result).contains("= 12");
        assertThat(result).doesNotContain("12.0");
    }

    @Test
    @DisplayName("含税金额计算")
    void calcTaxAmount() {
        String result = tool.calculateExpression("1000*0.13");
        assertThat(result).contains("= 130");
    }

    @Test
    @DisplayName("嵌套括号幂运算")
    void calcNestedPow() {
        String result = tool.calculateExpression("((1+2)*3)^2");
        assertThat(result).contains("= 81");
    }

    @Test
    @DisplayName("负数乘法")
    void calcNegativeMultiply() {
        String result = tool.calculateExpression("-5*-3");
        assertThat(result).contains("= 15");
    }

    @Test
    @DisplayName("对数换底公式")
    void calcLogChange() {
        String result = tool.calculateExpression("log(100)/log(10)");
        assertThat(result).startsWith("计算结果：");
        assertThat(result).contains("2");
    }

    @Test
    @DisplayName("abs 绝对值函数")
    void calcAbs() {
        String result = tool.calculateExpression("abs(-42)");
        assertThat(result).contains("= 42");
    }

    @Test
    @DisplayName("除零返回友好错误")
    void calcDivisionByZero() {
        String result = tool.calculateExpression("100/0");
        assertThat(result).startsWith("计算失败：");
    }

    @Test
    @DisplayName("空字符串返回错误")
    void calcEmptyExpression() {
        String result = tool.calculateExpression("");
        assertThat(result).startsWith("计算失败：");
    }

    @Test
    @DisplayName("null 表达式返回错误")
    void calcNullExpression() {
        String result = tool.calculateExpression(null);
        assertThat(result).startsWith("计算失败：");
    }

    @Test
    @DisplayName("非法变量名返回错误")
    void calcInvalidVariable() {
        String result = tool.calculateExpression("abc+1");
        assertThat(result).startsWith("计算失败：");
    }

    @Test
    @DisplayName("极大数正常返回")
    void calcLargeNumber() {
        String result = tool.calculateExpression("1000000*1000000");
        assertThat(result).contains("= 1000000000000");
    }
}
