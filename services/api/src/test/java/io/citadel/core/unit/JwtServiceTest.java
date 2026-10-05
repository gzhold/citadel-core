package io.citadel.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.citadel.core.config.CitadelProperties;
import io.citadel.core.entity.MemberRole;
import io.citadel.core.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String VALID_SECRET =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private static CitadelProperties properties(String secret, long ttlSeconds) {
    return new CitadelProperties(
        new CitadelProperties.Cors(List.of("http://localhost:3000")),
        new CitadelProperties.Jwt(secret, ttlSeconds, 7),
        "Citadel");
  }

  @Test
  @DisplayName("access token 生成后可解析出 userId/email/workspaceId/role")
  void roundTrip() {
    var service = new JwtService(properties(VALID_SECRET, 900));

    String token = service.generateAccessToken(7L, "alice@example.com", 3L, MemberRole.ADMIN);

    var claims = service.parseAccessToken(token);
    assertThat(claims.userId()).isEqualTo(7L);
    assertThat(claims.email()).isEqualTo("alice@example.com");
    assertThat(claims.workspaceId()).isEqualTo(3L);
    assertThat(claims.role()).isEqualTo(MemberRole.ADMIN);
  }

  @Test
  @DisplayName("被篡改的 token 校验失败")
  void rejectsTamperedToken() {
    var service = new JwtService(properties(VALID_SECRET, 900));
    String token = service.generateAccessToken(1L, "a@b.c", 1L, MemberRole.OWNER);

    assertThatThrownBy(() -> service.parseAccessToken(token + "x"))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("过期的 token 校验失败")
  void rejectsExpiredToken() {
    var service = new JwtService(properties(VALID_SECRET, -60));
    String token = service.generateAccessToken(1L, "a@b.c", 1L, MemberRole.OWNER);

    assertThatThrownBy(() -> service.parseAccessToken(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("密钥不足 32 字节时拒绝启动")
  void rejectsShortSecret() {
    assertThatThrownBy(() -> new JwtService(properties("short-secret", 900)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  @DisplayName("刷新令牌哈希稳定且长度为 64，两次生成互不相同")
  void refreshTokenHashing() {
    var service = new JwtService(properties(VALID_SECRET, 900));

    String first = service.generateRefreshToken();
    String second = service.generateRefreshToken();

    assertThat(first).isNotEqualTo(second);
    assertThat(service.hashToken(first)).hasSize(64).isEqualTo(service.hashToken(first));
    assertThat(service.hashToken(first)).isNotEqualTo(service.hashToken(second));
  }
}
