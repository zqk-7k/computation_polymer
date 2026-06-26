package com.vaspshow.backend.dto;

public record AuthUserResponse(
    String username,
    String displayName,
    String role,
    boolean authenticated
) {
}
