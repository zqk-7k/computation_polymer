package com.vaspshow.backend.controller;

import com.vaspshow.backend.dto.AuthSessionResponse;
import com.vaspshow.backend.dto.AuthUserResponse;
import com.vaspshow.backend.dto.LoginRequest;
import com.vaspshow.backend.dto.RegisterRequest;
import com.vaspshow.backend.dto.RoleUpdateRequest;
import com.vaspshow.backend.service.AuthService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthSessionResponse login(@RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/register")
  public AuthSessionResponse register(@RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @GetMapping("/me")
  public AuthUserResponse me(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return authService.me(authorization);
  }

  @PostMapping("/logout")
  public AuthUserResponse logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    authService.logout(authorization);
    return authService.me(null);
  }

  @GetMapping("/users")
  public List<AuthUserResponse> users(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return authService.listUsers(authorization);
  }

  @PatchMapping("/users/{username}/role")
  public AuthUserResponse updateRole(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String username,
      @RequestBody RoleUpdateRequest request
  ) {
    return authService.updateRole(authorization, username, request.role());
  }
}
