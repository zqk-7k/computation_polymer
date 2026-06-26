package com.vaspshow.backend.dto;

import java.util.List;

public record ConformerResponse(
    String datasetId,
    String groupId,
    int index,
    String smiles,
    List<String> species,
    double energy,
    double radiusOfGyration,
    String unitNote,
    List<AtomCoordinateResponse> atoms
) {
}
