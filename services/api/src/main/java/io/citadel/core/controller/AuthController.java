package io.citadel.core.controller;

import io.citadel.core.dto.AuthResponse;
import io.citadel.core.dto.CryptoKeyResponse;
import io.citadel.core.dto.LoginRequest;
import io.citadel.core.dto.RefreshRequest;
import io.citadel.core.dto.RegisterRequest;
import io.citadel.core.security.AuthCryptoService;
import io.citadel.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final AuthCryptoService authCryptoService;

  public AuthController(AuthService authService, AuthCryptoService authCryptoService) {
    this.authService = authService;
    this.authCryptoService = authCryptoService;
  }

  /** 客户端用它加密登录/注册的密码信封；keyId 轮换后客户端应重取并重试一次。 */
  @GetMapping("/crypto/key")
  public CryptoKeyResponse cryptoKey() {
    return authCryptoService.publicKeyResponse();
  }

  /** 注册即建租户：同一事务内创建用户、默认工作区与 OWNER 成员关系。 */
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /** 刷新令牌轮换：旧 refresh 一次性使用，重放会触发该用户全部令牌吊销。 */
  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request);
  }
}
