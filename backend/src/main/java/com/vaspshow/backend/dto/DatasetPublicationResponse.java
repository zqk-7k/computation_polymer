package com.vaspshow.backend.dto;

public record DatasetPublicationResponse(
    String datasetId,
    String datasetName,
    boolean published,
    String note,
    String updatedBy,
    String updatedAt,
    String grade,
    String runId,
    String decision
) {
}
