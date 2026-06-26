package com.vaspshow.backend.dto;

import java.util.List;
import java.util.Map;

public record DatasetRecordDetailResponse(
    long id,
    String datasetId,
    String sourceRecordId,
    String datasetName,
    String datasetSize,
    String datasetDescription,
    String materialName,
    String materialId,
    String forceField,
    String simulationType,
    String validatedStatus,
    String smiles,
    String polymerizationDegree,
    String radiusOfGyration,
    String chainConformation,
    String calculationSoftware,
    String ensemble,
    String temperature,
    String density,
    String glassTransitionTemperatureTg,
    String youngsModulus,
    String tensileStrength,
    String homo,
    String lumo,
    String homoLumoGap,
    String doi,
    String category,
    String calculationPlatform,
    String calculationTime,
    String energy,
    String composition,
    String atomCount,
    String charge,
    String spin,
    List<String> warnings,
    Map<String, String> extraProperties,
    List<AtomCoordinateResponse> atoms,
    List<List<Double>> lattice,
    String unitNote
) {
}
