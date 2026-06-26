package com.vaspshow.backend.dto;

import java.util.List;

/**
 * Aggregate result of running the automated proof-reading rules over a candidate.
 *
 * @param status      overall PASS | WARN | FAIL
 * @param summary     short human summary
 * @param validatedAt ISO timestamp of when validation ran
 * @param checks      individual check results
 */
public record ValidationReport(
    String status,
    String summary,
    String validatedAt,
    List<ValidationCheck> checks
) {
}
