package com.vaspshow.backend.dto;

public record QualityAuditRuleResponse(
    String key,
    String title,
    String category,
    int weight,
    String passCriteria,
    String failureAction
) {
}
