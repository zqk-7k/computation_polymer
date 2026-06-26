package com.vaspshow.backend.dto;

public record PropertyAvailabilityResponse(
    String key,
    String label,
    long count
) {
}
