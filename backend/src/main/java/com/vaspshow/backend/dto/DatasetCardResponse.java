package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetCardResponse(
    String id,
    String name,
    String method,
    String scale,
    String intro,
    boolean linkable,
    int moleculeGroups,
    long totalConformers,
    String atomCountRange,
    List<String> elements,
    String functional,
    String basisSet
) {
}
