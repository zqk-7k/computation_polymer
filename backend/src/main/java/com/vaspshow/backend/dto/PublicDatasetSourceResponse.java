package com.vaspshow.backend.dto;

public record PublicDatasetSourceResponse(
    String key,
    String name,
    String provider,
    String accessType,
    String url,
    String coverage,
    String adapterStatus
) {
}
