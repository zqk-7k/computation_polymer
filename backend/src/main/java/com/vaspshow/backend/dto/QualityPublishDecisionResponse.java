package com.vaspshow.backend.dto;

public record QualityPublishDecisionResponse(
    String decisionId,
    String datasetId,
    String runId,
    String reviewer,
    String decision,
    String grade,
    boolean published,
    String comment,
    String createdAt
) {
}
