package com.vaspshow.backend.dto;

import java.util.List;

/**
 * Editable subset of the discovery configuration. Every field is boxed so a null
 * means "leave unchanged"; only non-null fields are applied. Scheduling cron and
 * contact email stay yml-only on purpose (riskier / deployment concerns).
 */
public record DiscoveryConfigUpdateRequest(
    Boolean enabled,
    List<String> queries,
    Integer maxResultsPerQuery,
    Boolean datacite,
    Boolean zenodo,
    Boolean figshare,
    Boolean nomad,
    Boolean dryad,
    Boolean openaire,
    Boolean validateEnabled,
    Boolean autoPromoteEnabled,
    Integer autoPromoteMinScore,
    Integer autoPromoteMinRelevance,
    Boolean autoPromoteRequireLicense,
    Boolean autoPromoteRequireValidationPass,
    Integer autoPromoteMaxPerRun,
    Boolean autoArchiveEnabled,
    Integer autoArchiveMaxScore,
    Boolean autoAdaptEnabled,
    Integer autoAdaptMaxDownloadMb,
    Integer autoAdaptSampleMb
) {
}
