package com.vaspshow.backend.dto;

public record LoginRequest(
    String username,
    String password
) {
}
