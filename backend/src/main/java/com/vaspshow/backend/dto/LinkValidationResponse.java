package com.vaspshow.backend.dto;

import java.util.List;

public record LinkValidationResponse(
    int score,
    List<LinkCheckResponse> checks,
    List<String> recommendations
) {
}
