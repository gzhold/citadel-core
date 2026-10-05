package io.citadel.core.service;

import io.citadel.core.dto.AuthResponse;
import io.citadel.core.dto.LoginRequest;
import io.citadel.core.dto.MeResponse;
import io.citadel.core.dto.RefreshRequest;
import io.citadel.core.dto.RegisterRequest;

public interface AuthService {

  /** 注册即建租户：同一事务创建用户、默认工作区与 OWNER 成员关系。 */
  AuthResponse register(RegisterRequest request);

  AuthResponse login(LoginRequest request);

  /** 刷新令牌轮换；检测到重放时吊销该用户全部令牌。 */
  AuthResponse refresh(RefreshRequest request);

  void logout(RefreshRequest request);

  MeResponse me(long userId);
}
