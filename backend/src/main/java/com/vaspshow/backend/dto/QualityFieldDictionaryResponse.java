package com.vaspshow.backend.dto;

public record QualityFieldDictionaryResponse(
    String fieldKey,
    String label,
    long filled,
    long total,
    double coverage,
    boolean required,
    String unit,
    String note
) {
}
