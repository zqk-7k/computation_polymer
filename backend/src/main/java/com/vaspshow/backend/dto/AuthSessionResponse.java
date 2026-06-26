package com.vaspshow.backend.dto;

public record AuthSessionResponse(
    String token,
    AuthUserResponse user
) {
}
