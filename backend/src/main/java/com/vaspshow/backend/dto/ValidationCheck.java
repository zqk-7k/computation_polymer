package com.vaspshow.backend.dto;

/**
 * One automated proof-reading check over a discovered candidate.
 *
 * @param key    stable machine key (link / license / format / doi / fields / relevance / duplicate / metadata)
 * @param label  human-readable Chinese label
 * @param status PASS | WARN | FAIL
 * @param detail short human explanation of the result
 */
public record ValidationCheck(
    String key,
    String label,
    String status,
    String detail
) {
}
