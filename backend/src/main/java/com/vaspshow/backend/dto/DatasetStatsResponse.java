package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetStatsResponse(
    String datasetId,
    long total,
    List<HistogramBinResponse> atomCountHistogram,
    List<HistogramBinResponse> energyHistogram,
    List<HistogramBinResponse> gapHistogram,
    List<ElementCountResponse> elementCounts,
    List<PropertyAvailabilityResponse> availability
) {
}
