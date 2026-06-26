package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetQualityReportResponse(
    String runId,
    DatasetQualityResponse summary,
    List<QualityIssueResponse> issues,
    List<QualityFieldDictionaryResponse> fieldDictionary,
    DatasetPublicationResponse publication,
    String reportText
) {
}
