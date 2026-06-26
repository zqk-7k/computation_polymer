package com.vaspshow.backend.dto;

public record QualityRunResponse(
    String runId,
    String buildId,
    String generatedAt,
    String status,
    long totalDatasets,
    long totalRecords,
    int averageScore,
    String reportPath
) {
}
