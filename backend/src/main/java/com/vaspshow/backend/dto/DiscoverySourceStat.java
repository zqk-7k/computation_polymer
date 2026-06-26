package com.vaspshow.backend.dto;

/**
 * Per-source outcome of a single discovery run, so reviewers can tell a genuinely
 * empty source apart from one that failed (timeout, HTTP error, schema change).
 *
 * @param source human-readable source name (DataCite / Zenodo / NOMAD ...)
 * @param found  number of drafts the source returned this run (before cross-source dedup)
 * @param status OK | EMPTY | DISABLED | ERROR
 * @param detail short human note (dedup result or error message)
 */
public record DiscoverySourceStat(
    String source,
    int found,
    String status,
    String detail
) {
}
