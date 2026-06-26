package com.vaspshow.backend.dto;

public record DatasetSubmissionResponse(
    long id,
    String datasetName,
    String dataType,
    String description,
    String paperUrl,
    String dataUrl,
    String dataFormat,
    String license,
    String providedFields,
    String submittedBy,
    String submittedAt,
    String status,
    String reviewNote,
    String reviewedAt,
    String pipelineStage,
    String pipelineMessage,
    String updatedAt,
    String parseProfile
) {
}
