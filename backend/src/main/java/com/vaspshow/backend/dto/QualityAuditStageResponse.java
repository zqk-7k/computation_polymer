package com.vaspshow.backend.dto;

public record QualityAuditStageResponse(
    String key,
    String title,
    String owner,
    String status,
    String purpose,
    String evidence
) {
}
