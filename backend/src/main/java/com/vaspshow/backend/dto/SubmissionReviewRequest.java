package com.vaspshow.backend.dto;

public record SubmissionReviewRequest(
    String decision,
    String note
) {
}
