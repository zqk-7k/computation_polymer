package com.vaspshow.backend.dto;

import java.util.List;
import java.util.Map;

public record IngestSuggestionResponse(
    String datasetKey,
    String datasetName,
    boolean supported,
    String format,
    List<String> sourceFields,
    Map<String, String> suggestedMapping,
    String note
) {
}
