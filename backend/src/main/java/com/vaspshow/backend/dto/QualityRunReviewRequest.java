package com.vaspshow.backend.dto;

public record QualityRunReviewRequest(
    String reviewerRole,
    String conclusion,
    String note
) {
}
