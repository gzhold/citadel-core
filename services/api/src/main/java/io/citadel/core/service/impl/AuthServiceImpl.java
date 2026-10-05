package io.citadel.core.service.impl;

import io.citadel.core.audit.AuditActions;
import io.citadel.core.audit.AuditEventPublisher;
import io.citadel.core.audit.AuditedEvent;
import io.citadel.core.config.CitadelProperties;
import io.citadel.core.dto.AuthResponse;
import io.citadel.core.dto.LoginRequest;
import io.citadel.core.dto.MeResponse;
import io.citadel.core.dto.MembershipDto;
import io.citadel.core.dto.RefreshRequest;
import io.citadel.core.dto.RegisterRequest;
import io.citadel.core.dto.UserDto;
import io.citadel.core.dto.WorkspaceDto;
import io.citadel.core.entity.MemberRole;
import io.citadel.core.entity.RefreshToken;
import io.citadel.core.entity.User;
import io.citadel.core.entity.Workspace;
import io.citadel.core.entity.WorkspaceMember;
import io.citadel.core.entity.WorkspacePlan;
import io.citadel.core.exception.ApiException;
import io.citadel.core.repository.RefreshTokenRepository;
import io.citadel.core.repository.UserRepository;
import io.citadel.core.repository.WorkspaceMemberRepository;
import io.citadel.core.repository.WorkspaceRepository;
import io.citadel.core.security.JwtService;
import io.citadel.core.service.AuthService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

  /**
   * 未知邮箱也执行一次等耗时的 BCrypt 比对，缓解通过响应时间探测邮箱是否注册的枚举攻击。 （bcrypt 哈希必须在运行期真实生成，伪造字符串会在 matches 时抛出 Invalid
   * salt。）
   */
  private static final String DUMMY_BCRYPT_HASH =
      new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
          .encode("citadel-timing-equalizer");

  private static final Pattern SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
  private static final String SLUG_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditEventPublisher auditPublisher;
  private final CitadelProperties properties;

  public AuthServiceImpl(
      UserRepository userRepository,
      WorkspaceRepository workspaceRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuditEventPublisher auditPublisher,
      CitadelProperties properties) {
    this.userRepository = userRepository;
    this.workspaceRepository = workspaceRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.auditPublisher = auditPublisher;
    this.properties = properties;
  }

  @Override
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_USED", "该邮箱已被注册");
    }

    User user =
        userRepository.save(
            User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .build());

    Workspace workspace =
        workspaceRepository.save(
            Workspace.builder()
                .name(request.displayName().trim() + "'s Workspace")
                .slug(uniqueSlug(request.displayName()))
                .plan(WorkspacePlan.FREE)
                .build());

    workspaceMemberRepository.save(
        WorkspaceMember.builder()
            .workspace(workspace)
            .user(user)
            .role(MemberRole.OWNER)
            .joinedAt(Instant.now())
            .build());

    auditPublisher.publish(
        new AuditedEvent(
            null, user.getId(), AuditActions.USER_REGISTERED, "user:" + user.getId(), email));
    auditPublisher.publish(
        new AuditedEvent(
            workspace.getId(),
            user.getId(),
            AuditActions.WORKSPACE_CREATED,
            "workspace:" + workspace.getId(),
            workspace.getSlug()));

    return issueTokens(user, workspace, MemberRole.OWNER);
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> {
                  // 时间均衡：未知邮箱同样执行一次 BCrypt 比对
                  passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
                  return invalidCredentials();
                });

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditPublisher.publish(
          new AuditedEvent(
              null, user.getId(), AuditActions.AUTH_LOGIN_FAILED, "user:" + user.getId(), email));
      throw invalidCredentials();
    }

    WorkspaceMember membership = defaultMembership(user.getId());
    auditPublisher.publish(
        new AuditedEvent(
            membership.getWorkspace().getId(),
            user.getId(),
            AuditActions.AUTH_LOGIN,
            "user:" + user.getId(),
            email));

    return issueTokens(user, membership.getWorkspace(), membership.getRole());
  }

  @Override
  @Transactional
  public AuthResponse refresh(RefreshRequest request) {
    String hash = jwtService.hashToken(request.refreshToken());
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> unauthorized("INVALID_REFRESH_TOKEN", "无效的刷新令牌"));

    Instant now = Instant.now();
    if (token.isRevoked()) {
      // 重放已吊销令牌：视为泄露，吊销该用户全部活跃令牌
      refreshTokenRepository.revokeAllActiveByUserId(token.getUserId(), now);
      auditPublisher.publish(
          new AuditedEvent(
              null,
              token.getUserId(),
              AuditActions.AUTH_TOKEN_REUSE_DETECTED,
              "refresh_token:" + token.getId(),
              null));
      throw unauthorized("REFRESH_TOKEN_REVOKED", "刷新令牌已失效，请重新登录");
    }
    if (token.isExpired(now)) {
      token.setRevokedAt(now);
      refreshTokenRepository.save(token);
      throw unauthorized("REFRESH_TOKEN_EXPIRED", "刷新令牌已过期，请重新登录");
    }

    token.setRevokedAt(now); // 轮换：一次性使用
    refreshTokenRepository.save(token);

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> unauthorized("INVALID_REFRESH_TOKEN", "无效的刷新令牌"));
    WorkspaceMember membership = defaultMembership(user.getId());
    auditPublisher.publish(
        new AuditedEvent(
            membership.getWorkspace().getId(),
            user.getId(),
            AuditActions.AUTH_REFRESH,
            "user:" + user.getId(),
            null));

    return issueTokens(user, membership.getWorkspace(), membership.getRole());
  }

  @Override
  @Transactional
  public void logout(RefreshRequest request) {
    String hash = jwtService.hashToken(request.refreshToken());
    refreshTokenRepository
        .findByTokenHash(hash)
        .filter(token -> !token.isRevoked())
        .ifPresent(
            token -> {
              token.setRevokedAt(Instant.now());
              refreshTokenRepository.save(token);
              auditPublisher.publish(
                  new AuditedEvent(
                      null,
                      token.getUserId(),
                      AuditActions.AUTH_LOGOUT,
                      "user:" + token.getUserId(),
                      null));
            });
  }

  @Override
  @Transactional(readOnly = true)
  public MeResponse me(long userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> unauthorized("UNAUTHENTICATED", "用户不存在"));
    var memberships =
        workspaceMemberRepository.findByUserIdOrderByJoinedAtAsc(userId).stream()
            .map(
                member ->
                    new MembershipDto(
                        member.getWorkspace().getId(),
                        member.getWorkspace().getName(),
                        member.getWorkspace().getSlug(),
                        member.getRole()))
            .toList();
    return new MeResponse(toUserDto(user), memberships);
  }

  private AuthResponse issueTokens(User user, Workspace workspace, MemberRole role) {
    String accessToken =
        jwtService.generateAccessToken(user.getId(), user.getEmail(), workspace.getId(), role);
    String refreshToken = jwtService.generateRefreshToken();
    refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(user.getId())
            .tokenHash(jwtService.hashToken(refreshToken))
            .expiresAt(Instant.now().plusSeconds(properties.jwt().refreshTtlDays() * 86_400L))
            .build());
    return new AuthResponse(
        accessToken,
        refreshToken,
        AuthResponse.BEARER,
        properties.jwt().accessTtlSeconds(),
        toUserDto(user),
        new WorkspaceDto(workspace.getId(), workspace.getName(), workspace.getSlug(), role));
  }

  private WorkspaceMember defaultMembership(long userId) {
    return workspaceMemberRepository
        .findFirstByUserIdOrderByJoinedAtAsc(userId)
        .orElseThrow(
            () -> new ApiException(HttpStatus.FORBIDDEN, "NO_WORKSPACE", "用户不属于任何工作区，请联系支持"));
  }

  private static UserDto toUserDto(User user) {
    return new UserDto(user.getId(), user.getEmail(), user.getDisplayName());
  }

  private static ApiException invalidCredentials() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
  }

  private static ApiException unauthorized(String code, String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
  }

  /** slug = 名称转写 + 6 位随机后缀，冲突时重试；非 ASCII 名称回退为 "team"。 */
  private String uniqueSlug(String displayName) {
    String base = SLUG_CHARS.matcher(displayName.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
    base = base.replaceAll("(^-+|-+$)", "");
    if (base.isEmpty()) {
      base = "team";
    }
    String candidate;
    do {
      StringBuilder suffix = new StringBuilder();
      for (int i = 0; i < 6; i++) {
        suffix.append(SLUG_ALPHABET.charAt(RANDOM.nextInt(SLUG_ALPHABET.length())));
      }
      candidate = base + "-" + suffix;
    } while (workspaceRepository.existsBySlug(candidate));
    return candidate;
  }
}
