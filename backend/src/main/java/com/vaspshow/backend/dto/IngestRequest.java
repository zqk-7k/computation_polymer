package com.vaspshow.backend.dto;

import java.util.Map;

public record IngestRequest(
    String datasetName,
    Map<String, String> mapping,
    Integer limit
) {
}
