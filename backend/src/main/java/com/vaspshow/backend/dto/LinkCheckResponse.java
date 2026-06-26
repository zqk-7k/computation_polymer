package com.vaspshow.backend.dto;

public record LinkCheckResponse(
    String key,
    String label,
    String url,
    boolean reachable,
    int statusCode,
    String finalUrl,
    String message
) {
}
