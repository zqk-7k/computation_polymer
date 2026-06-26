package com.vaspshow.backend.dto;

public record QualityGateResponse(
    String key,
    String title,
    String status,
    int score,
    String description
) {
}
