package com.vaspshow.backend.dto;

import java.util.List;

public record DiscoveryRunResponse(
    String runId,
    String startedAt,
    String finishedAt,
    String status,
    int discovered,
    int inserted,
    int updated,
    int autoPromoted,
    int autoArchived,
    String message,
    List<DiscoverySourceStat> sourceStats
) {
}
