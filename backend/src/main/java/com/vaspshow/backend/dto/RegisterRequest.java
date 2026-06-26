package com.vaspshow.backend.dto;

public record RegisterRequest(
    String username,
    String displayName,
    String password
) {
}
