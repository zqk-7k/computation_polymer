package com.vaspshow.backend.dto;

public record QualityIssueReviewRequest(
    String status,
    String owner,
    String note
) {
}
