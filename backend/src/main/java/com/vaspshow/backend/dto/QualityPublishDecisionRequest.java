package com.vaspshow.backend.dto;

public record QualityPublishDecisionRequest(
    String runId,
    String decision,
    String grade,
    boolean published,
    String comment
) {
}
