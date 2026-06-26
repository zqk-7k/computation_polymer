package com.vaspshow.backend.dto;

import java.util.List;

public record DatasetCatalogResponse(
    String id,
    String name,
    String dataType,
    String intro,
    String scale,
    long displayRecords,
    long sourceStructures,
    int minAtoms,
    int maxAtoms,
    List<String> elements,
    List<String> calculationMethods,
    List<String> functionals,
    List<String> basisSets,
    List<String> software,
    List<String> properties,
    String representation,
    List<DatasetLinkResponse> links
) {
}
