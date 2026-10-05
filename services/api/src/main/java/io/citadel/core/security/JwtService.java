package io.citadel.core.security;

import io.citadel.core.config.CitadelProperties;
import io.citadel.core.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p>access token 携带租户上下文（wid）与角色（role）—— 这是 F1「跨租户越权防护」与 F2
 * 「RBAC」在令牌层的基础：所有受保护接口的租户归属均以该声明为准，绝不信任请求参数中的 workspaceId。
 */
@Component
public class JwtService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey key;
  private final long accessTtlSeconds;

  public JwtService(CitadelProperties properties) {
    this.accessTtlSeconds = properties.jwt().accessTtlSeconds();
    this.key = Keys.hmacShaKeyFor(decodeSecret(properties.jwt().secret()));
  }

  /** access token 中的已验证声明。 */
  public record AccessTokenClaims(long userId, String email, long workspaceId, MemberRole role) {}

  public String generateAccessToken(long userId, String email, long workspaceId, MemberRole role) {
    var now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("wid", workspaceId)
        .claim("role", role.name())
        .claim("typ", "access")
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plusSeconds(accessTtlSeconds)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /** 校验签名与有效期并返回声明；任何异常以 JwtException 抛出。 */
  public AccessTokenClaims parseAccessToken(String token) {
    Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    if (!"access".equals(claims.get("typ", String.class))) {
      throw new JwtException("token is not an access token");
    }
    return new AccessTokenClaims(
        Long.parseLong(claims.getSubject()),
        claims.get("email", String.class),
        ((Number) claims.get("wid")).longValue(),
        MemberRole.valueOf(claims.get("role", String.class)));
  }

  /** 刷新令牌为 256-bit 随机数（非 JWT），服务端仅存储其 SHA-256 哈希。 */
  public String generateRefreshToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hashToken(String rawToken) {
    try {
      var digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** 密钥既支持 Base64（推荐，openssl rand -base64 48）也支持原始字符串； 解码后不足 32 字节直接拒绝启动。 */
  private static byte[] decodeSecret(String secret) {
    var raw = secret.getBytes(StandardCharsets.UTF_8);
    byte[] candidate = raw;
    try {
      var decoded = Base64.getDecoder().decode(secret);
      if (decoded.length >= 32) {
        candidate = decoded;
      }
    } catch (IllegalArgumentException ignored) {
      // 不是合法 Base64，按原始字符串处理
    }
    if (candidate.length < 32) {
      throw new IllegalStateException(
          "JWT_SECRET 至少需要 32 字节（推荐：openssl rand -base64 48 生成后配置到环境变量）");
    }
    return candidate;
  }
}
