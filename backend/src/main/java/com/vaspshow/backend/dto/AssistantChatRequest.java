package com.vaspshow.backend.dto;

import java.util.List;

public record AssistantChatRequest(
    List<AssistantChatMessageRequest> messages,
    String datasetId,
    Long recordId
) {
}
