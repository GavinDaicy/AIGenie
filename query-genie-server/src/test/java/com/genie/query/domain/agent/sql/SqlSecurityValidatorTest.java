package com.genie.query.domain.agent.sql;

import com.genie.query.domain.agent.tool.sql.pipeline.SqlSecurityValidator;
import com.genie.query.domain.agent.tool.sql.model.ValidationResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlSecurityValidator 单元测试：覆盖各类注入和违规场景。
 *
 * @author daicy
 * @date 2026/4/2
 */
class SqlSecurityValidatorTest {

    private SqlSecurityValidator validator;
    private static final Set<String> ALLOWED = Set.of("steel_price", "order_record", "customer_info");

    @BeforeEach
    void setUp() {
        validator = new SqlSecurityValidator();
    }

    // --- 合法 SELECT 应通过 ---

    @Test
    void validate_shouldPass_forNormalSelect() {
        String sql = "SELECT supplier_name, MIN(unit_price) FROM steel_price WHERE steel_diameter = 20 LIMIT 10";
        assertThat(validator.validate(sql, ALLOWED).isPass()).isTrue();
    }

    @Test
    void validate_shouldPass_forSelectWithJoin() {
        String sql = "SELECT s.supplier_name, o.quantity FROM steel_price s JOIN order_record o ON s.id = o.price_id LIMIT 20";
        assertThat(validator.validate(sql, ALLOWED).isPass()).isTrue();
    }

    @Test
    void validate_shouldPass_forSelectWithSubquery() {
        String sql = "SELECT * FROM (SELECT supplier_name, AVG(unit_price) as avg FROM steel_price GROUP BY supplier_name) t LIMIT 10";
        assertThat(validator.validate(sql, ALLOWED).isPass()).isTrue();
    }

    // --- 禁止关键字 ---

    @Test
    void validate_shouldFail_forInsert() {
        String sql = "INSERT INTO steel_price (supplier_name) VALUES ('hack')";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).contains("禁止");
    }

    @Test
    void validate_shouldFail_forUpdate() {
        ValidationResult result = validator.validate("UPDATE steel_price SET unit_price = 0", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forDelete() {
        ValidationResult result = validator.validate("DELETE FROM steel_price WHERE id = 1", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forDrop() {
        ValidationResult result = validator.validate("DROP TABLE steel_price", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forTruncate() {
        ValidationResult result = validator.validate("TRUNCATE steel_price", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forAlter() {
        ValidationResult result = validator.validate("ALTER TABLE steel_price ADD COLUMN hack VARCHAR(100)", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forExecute() {
        ValidationResult result = validator.validate("EXECUTE sp_some_proc", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    // --- 多语句注入 ---

    @Test
    void validate_shouldFail_forMultiStatement() {
        String sql = "SELECT * FROM steel_price LIMIT 10; SELECT * FROM order_record LIMIT 10";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).contains("多条");
    }

    // --- 注释注入 ---

    @Test
    void validate_shouldFail_forLineComment() {
        String sql = "SELECT * FROM steel_price -- WHERE 1=0\nLIMIT 10";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).contains("注释");
    }

    @Test
    void validate_shouldFail_forHashComment() {
        String sql = "SELECT * FROM steel_price # comment\nLIMIT 10";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forBlockComment() {
        String sql = "SELECT * FROM steel_price /* injected */ LIMIT 10";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    // --- 表名白名单 ---

    @Test
    void validate_shouldFail_forUnregisteredTable() {
        String sql = "SELECT * FROM users LIMIT 10";
        ValidationResult result = validator.validate(sql, ALLOWED);
        assertThat(result.isPass()).isFalse();
        assertThat(result.getReason()).contains("users");
    }

    @Test
    void validate_shouldPass_withNullAllowedTables() {
        String sql = "SELECT * FROM any_table LIMIT 10";
        assertThat(validator.validate(sql, null).isPass()).isTrue();
    }

    @Test
    void validate_shouldPass_withEmptyAllowedTables() {
        String sql = "SELECT * FROM any_table LIMIT 10";
        assertThat(validator.validate(sql, Set.of()).isPass()).isTrue();
    }

    // --- LIMIT 强制追加 ---

    @Test
    void enforceLimitClause_shouldAppendLimit_whenMissing() {
        String sql = "SELECT * FROM steel_price";
        String result = validator.enforceLimitClause(sql, 500);
        assertThat(result).endsWith("LIMIT 500");
    }

    @Test
    void enforceLimitClause_shouldReplaceExistingLimit() {
        String sql = "SELECT * FROM steel_price LIMIT 1000";
        String result = validator.enforceLimitClause(sql, 500);
        assertThat(result).contains("LIMIT 500");
        assertThat(result).doesNotContain("LIMIT 1000");
    }

    @Test
    void enforceLimitClause_shouldUseDefaultMaxRows_whenNotSpecified() {
        String sql = "SELECT * FROM steel_price";
        String result = validator.enforceLimitClause(sql);
        assertThat(result).contains("LIMIT " + SqlSecurityValidator.DEFAULT_MAX_ROWS);
    }

    // --- extractTableNames ---

    @Test
    void extractTableNames_shouldExtractFromClause() {
        String sql = "SELECT * FROM steel_price WHERE id = 1";
        assertThat(validator.extractTableNames(sql)).containsExactly("steel_price");
    }

    @Test
    void extractTableNames_shouldExtractJoinTables() {
        String sql = "SELECT * FROM steel_price s JOIN order_record o ON s.id = o.price_id";
        assertThat(validator.extractTableNames(sql)).containsExactlyInAnyOrder("steel_price", "order_record");
    }

    @Test
    void extractTableNames_shouldHandleBacktickQuotes() {
        String sql = "SELECT * FROM `steel_price` LIMIT 10";
        assertThat(validator.extractTableNames(sql)).containsExactly("steel_price");
    }

    // --- 空/null 输入 ---

    @Test
    void validate_shouldFail_forBlankSql() {
        ValidationResult result = validator.validate("   ", ALLOWED);
        assertThat(result.isPass()).isFalse();
    }

    @Test
    void validate_shouldFail_forNullSql() {
        ValidationResult result = validator.validate(null, ALLOWED);
        assertThat(result.isPass()).isFalse();
    }
}
