package io.citadel.core.controller;

import io.citadel.core.dto.MeResponse;
import io.citadel.core.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 受保护接口示例：principal 为 JwtAuthenticationFilter 注入的 userId。 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

  private final AuthService authService;

  public MeController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/me")
  public MeResponse me(Authentication authentication) {
    long userId = (Long) authentication.getPrincipal();
    return authService.me(userId);
  }
}
