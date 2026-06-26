package com.vaspshow.backend.dto;

public record QualityRunReviewResponse(
    String reviewId,
    String runId,
    String reviewer,
    String reviewerRole,
    String conclusion,
    String note,
    String createdAt
) {
}
