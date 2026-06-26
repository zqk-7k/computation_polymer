package com.vaspshow.backend.dto;

public record LinkValidationRequest(
    String doi,
    String paperUrl,
    String dataUrl
) {
}
