package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetDetailResponse(
    String id,
    String title,
    String rootGroup,
    String method,
    String scale,
    String intro,
    int moleculeGroupCount,
    long totalConformers,
    int minAtoms,
    int maxAtoms,
    List<String> elements,
    List<GroupSummaryResponse> groups,
    List<DatasetLinkResponse> links
) {
}
