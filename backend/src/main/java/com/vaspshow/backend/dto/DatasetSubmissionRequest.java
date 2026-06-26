package com.vaspshow.backend.dto;

public record DatasetSubmissionRequest(
    String datasetName,
    String dataType,
    String description,
    String paperUrl,
    String dataUrl,
    String dataFormat,
    String license,
    String providedFields
) {
}
