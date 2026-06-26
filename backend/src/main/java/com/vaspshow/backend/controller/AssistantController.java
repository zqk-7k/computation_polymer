package com.vaspshow.backend.controller;

import com.vaspshow.backend.dto.AssistantChatRequest;
import com.vaspshow.backend.dto.AssistantChatResponse;
import com.vaspshow.backend.dto.AuthUserResponse;
import com.vaspshow.backend.service.AssistantService;
import com.vaspshow.backend.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;
  private final AuthService authService;

  public AssistantController(AssistantService assistantService, AuthService authService) {
    this.assistantService = assistantService;
    this.authService = authService;
  }

  @PostMapping("/chat")
  public AssistantChatResponse chat(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody AssistantChatRequest request
  ) {
    AuthUserResponse user = authService.requireAssistantAccess(authorization);
    return assistantService.chat(request, user);
  }
}
