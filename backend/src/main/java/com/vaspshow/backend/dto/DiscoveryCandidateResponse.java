package com.vaspshow.backend.dto;

import java.util.List;

public record DiscoveryCandidateResponse(
    long id,
    String source,
    String title,
    String doi,
    String paperUrl,
    String dataUrl,
    String repository,
    String license,
    String dataFormat,
    String dataScale,
    String method,
    String detectedFields,
    String parserPlan,
    int score,
    int relevance,
    String recommendation,
    String status,
    String reviewNote,
    String discoveredAt,
    String updatedAt,
    String validationStatus,
    String validatedAt,
    List<ValidationCheck> validationChecks
) {
}
