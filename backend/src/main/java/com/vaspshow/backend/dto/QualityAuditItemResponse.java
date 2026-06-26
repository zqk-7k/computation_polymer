package com.vaspshow.backend.dto;

public record QualityAuditItemResponse(
    String key,
    String label,
    String status,
    int score,
    String evidence,
    String action
) {
}
