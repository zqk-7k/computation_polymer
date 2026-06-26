package com.vaspshow.backend.dto;

public record QualityIssueReviewResponse(
    String issueId,
    String status,
    String owner,
    String note,
    String reviewer,
    String updatedAt
) {
}
