package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetRecordsResponse(
    String datasetId,
    long total,
    int offset,
    int limit,
    List<DatasetRecordSummaryResponse> records
) {
}
