package com.vaspshow.backend.dto;

public record HistogramBinResponse(
    String label,
    double min,
    double max,
    long count
) {
}
