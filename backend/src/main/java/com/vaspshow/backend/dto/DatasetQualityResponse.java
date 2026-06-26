package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetQualityResponse(
    String datasetId,
    String name,
    String type,
    long totalRecords,
    int score,
    String level,
    double completenessScore,
    double structureScore,
    double numericScore,
    double traceabilityScore,
    boolean structureExpected,
    boolean forceExpected,
    long warningCount,
    String atomCountRange,
    String energyRange,
    List<QualityCoverageMetricResponse> metrics,
    List<String> recommendations,
    List<QualityAuditItemResponse> auditItems,
    List<String> missingFields,
    String reviewStatus,
    String publishTier,
    String auditSummary
) {
}
