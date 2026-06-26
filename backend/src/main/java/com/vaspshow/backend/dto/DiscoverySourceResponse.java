package com.vaspshow.backend.dto;

public record DiscoverySourceResponse(
    String key,
    String name,
    String url,
    String status,
    String cadence,
    String capability
) {
}
