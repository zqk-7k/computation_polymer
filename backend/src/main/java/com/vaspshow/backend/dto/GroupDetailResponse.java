package com.vaspshow.backend.dto;

public record GroupDetailResponse(
    String datasetId,
    GroupSummaryResponse group
) {
}
