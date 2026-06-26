package com.vaspshow.backend.dto;

public record QualityIssueResponse(
    String datasetId,
    String datasetName,
    String severity,
    String title,
    String detail,
    String suggestion
) {
}
