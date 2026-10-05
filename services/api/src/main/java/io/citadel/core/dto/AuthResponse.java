package io.citadel.core.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserDto user,
    WorkspaceDto workspace) {

  public static final String BEARER = "Bearer";
}
