package com.vaspshow.backend.dto;

public record DatasetRecordSummaryResponse(
    long id,
    String datasetId,
    String sourceRecordId,
    String datasetName,
    String materialName,
    String materialId,
    String smiles,
    String composition,
    String atomCount,
    String energy,
    String homo,
    String lumo,
    String homoLumoGap,
    String charge,
    String spin,
    String calculationSoftware
) {
}
