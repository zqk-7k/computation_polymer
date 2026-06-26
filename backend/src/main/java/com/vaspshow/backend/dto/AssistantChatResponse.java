package com.vaspshow.backend.dto;

import java.util.List;

public record AssistantChatResponse(
    String model,
    String answer,
    String contextLabel,
    List<String> groundedSources
) {
}
