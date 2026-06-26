package com.vaspshow.backend.dto;

import java.util.List;

public record FilePreviewResponse(
    String filename,
    String format,
    long fileSize,
    int sampledRecords,
    int score,
    String summary,
    List<FileFieldProfileResponse> fields,
    List<String> detectedFields,
    List<String> missingFields,
    List<String> recommendations
) {
}
