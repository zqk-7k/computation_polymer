package com.vaspshow.backend.dto;

public record QualityCoverageMetricResponse(
    String key,
    String label,
    long filled,
    long total,
    double ratio,
    boolean expected
) {
}
