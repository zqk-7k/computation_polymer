package com.vaspshow.backend.dto;

import java.util.List;

public record DiscoveryConfigResponse(
    boolean enabled,
    String cron,
    boolean datacite,
    boolean zenodo,
    boolean nomad,
    boolean figshare,
    boolean dryad,
    boolean openaire,
    int maxResultsPerQuery,
    String contactEmail,
    List<String> queries,
    boolean validateEnabled,
    boolean autoPromoteEnabled,
    int autoPromoteMinScore,
    int autoPromoteMinRelevance,
    boolean autoPromoteRequireLicense,
    boolean autoPromoteRequireValidationPass,
    int autoPromoteMaxPerRun,
    boolean autoArchiveEnabled,
    int autoArchiveMaxScore,
    boolean autoAdaptEnabled,
    int autoAdaptMaxDownloadMb,
    int autoAdaptSampleMb
) {
}
