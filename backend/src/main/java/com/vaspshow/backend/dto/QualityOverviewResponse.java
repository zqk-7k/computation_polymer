package com.vaspshow.backend.dto;

import java.util.List;

public record QualityOverviewResponse(
    String generatedAt,
    long totalDatasets,
    long totalRecords,
    int averageScore,
    String scope,
    List<QualityGateResponse> gates,
    List<DatasetQualityResponse> datasets,
    List<QualityIssueResponse> issues,
    List<QualityAuditStageResponse> auditStages,
    List<QualityAuditRuleResponse> auditRules
) {
}
