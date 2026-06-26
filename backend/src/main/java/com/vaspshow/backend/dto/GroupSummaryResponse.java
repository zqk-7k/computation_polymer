package com.vaspshow.backend.dto;

import java.util.List;

public record GroupSummaryResponse(
    String id,
    String smiles,
    List<String> species,
    int atomCount,
    int conformerCount,
    int highEnergyConformerCount,
    double energyMin,
    double energyMax,
    double energyMean,
    List<Double> energySamples
) {
}
