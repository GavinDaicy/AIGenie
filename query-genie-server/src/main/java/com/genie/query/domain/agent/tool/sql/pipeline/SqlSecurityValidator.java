package com.genie.query.domain.agent.tool.sql.pipeline;

import com.genie.query.domain.agent.tool.sql.model.ValidationResult;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器（Step 3）：对 LLM 生成的 SQL 进行多维度安全检查，防止注入、DDL 操作和越权访问。
 *
 * <p>校验规则：
 * <ol>
 *   <li>禁止关键字：INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE/EXEC/EXECUTE</li>
 *   <li>禁止多语句（防 {@code ;} 注入）</li>
 *   <li>禁止 SQL 注释（防注释绕过）</li>
 *   <li>表名白名单校验（仅允许已在 db_table_schema 中注册的表）</li>
 * </ol>
 *
 * @author daicy
 * @date 2026/4/2
 */
@Component
public class SqlSecurityValidator {

    /** 规则1：禁止 DML/DDL/权限关键字 */
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE|CALL|MERGE|REPLACE|LOAD)\\b"
    );

    /** 规则2：禁止多语句（; 后跟非空白字符） */
    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S");

    /** 规则3：禁止注释（行注释 -- 或 #，块注释 / * * /） */
    private static final Pattern SQL_COMMENT = Pattern.compile("(/\\*|\\*/|--\\s|--$|#)");

    /** 提取 FROM/JOIN 后的表名（处理别名） */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)(?:FROM|JOIN)\\s+([`\"\\[]?[\\w]+[`\"\\]]?)(?:\\s+(?:AS\\s+)?[\\w]+)?",
            Pattern.CASE_INSENSITIVE
    );

    /** 默认最大行数限制 */
    public static final int DEFAULT_MAX_ROWS = 500;

    /**
     * 校验 SQL 是否安全合规。
     *
     * @param sql           待校验的 SQL 语句
     * @param allowedTables 白名单表名集合（小写），来自 db_table_schema 注册的表
     * @return 校验结果
     */
    public ValidationResult validate(String sql, Set<String> allowedTables) {
        if (sql == null || sql.isBlank()) {
            return ValidationResult.fail("SQL 语句为空");
        }

        if (FORBIDDEN_KEYWORDS.matcher(sql).find()) {
            return ValidationResult.fail("包含禁止的 SQL 操作类型（仅允许 SELECT）");
        }

        if (MULTI_STATEMENT.matcher(sql).find()) {
            return ValidationResult.fail("不允许多条 SQL 语句（禁止使用 ; 分隔）");
        }

        if (SQL_COMMENT.matcher(sql).find()) {
            return ValidationResult.fail("不允许包含 SQL 注释（防止注释绕过校验）");
        }

        if (allowedTables != null && !allowedTables.isEmpty()) {
            Set<String> usedTables = extractTableNames(sql);
            for (String table : usedTables) {
                if (!allowedTables.contains(table.toLowerCase())) {
                    return ValidationResult.fail("表 [" + table + "] 未在允许列表中");
                }
            }
        }

        return ValidationResult.pass();
    }

    /**
     * 强制追加 / 替换 LIMIT 子句，确保结果集不超过 {@code maxRows} 行。
     *
     * @param sql     原始 SQL
     * @param maxRows 最大行数
     * @return 带安全 LIMIT 的 SQL
     */
    public String enforceLimitClause(String sql, int maxRows) {
        String trimmed = sql.trim();
        if (!trimmed.toUpperCase().contains("LIMIT")) {
            return trimmed + " LIMIT " + maxRows;
        }
        return trimmed.replaceAll("(?i)LIMIT\\s+\\d+", "LIMIT " + maxRows);
    }

    /**
     * 用默认最大行数强制 LIMIT。
     */
    public String enforceLimitClause(String sql) {
        return enforceLimitClause(sql, DEFAULT_MAX_ROWS);
    }

    /**
     * 从 SQL 中提取 FROM / JOIN 后的表名（去除反引号/双引号/中括号）。
     */
    public Set<String> extractTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        Matcher matcher = TABLE_REF.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1)
                    .replaceAll("[`\"\\[\\]]", "")
                    .toLowerCase();
            tables.add(tableName);
        }
        return tables;
    }
}
