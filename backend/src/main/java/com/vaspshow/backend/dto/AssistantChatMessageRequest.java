package com.vaspshow.backend.dto;

public record AssistantChatMessageRequest(
    String role,
    String content
) {
}
