package com.vaspshow.backend.dto;

public record DatasetPublicationRequest(
    boolean published,
    String note,
    String grade,
    String runId,
    String decision
) {
}
