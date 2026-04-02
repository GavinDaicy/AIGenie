package com.genie.query.domain.agent.sql;

import lombok.Data;

/**
 * SQL 安全校验结果。
 *
 * @author daicy
 * @date 2026/4/2
 */
@Data
public class ValidationResult {

    private boolean pass;
    private String reason;

    public static ValidationResult pass() {
        ValidationResult r = new ValidationResult();
        r.setPass(true);
        return r;
    }

    public static ValidationResult fail(String reason) {
        ValidationResult r = new ValidationResult();
        r.setPass(false);
        r.setReason(reason);
        return r;
    }
}
