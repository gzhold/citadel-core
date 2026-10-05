package io.citadel.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.citadel.core.audit.AuditActions;
import io.citadel.core.audit.AuditEventPublisher;
import io.citadel.core.audit.AuditedEvent;
import io.citadel.core.config.CitadelProperties;
import io.citadel.core.dto.AuthResponse;
import io.citadel.core.dto.LoginRequest;
import io.citadel.core.dto.RefreshRequest;
import io.citadel.core.dto.RegisterRequest;
import io.citadel.core.entity.MemberRole;
import io.citadel.core.entity.RefreshToken;
import io.citadel.core.entity.User;
import io.citadel.core.entity.Workspace;
import io.citadel.core.entity.WorkspaceMember;
import io.citadel.core.exception.ApiException;
import io.citadel.core.repository.RefreshTokenRepository;
import io.citadel.core.repository.UserRepository;
import io.citadel.core.repository.WorkspaceMemberRepository;
import io.citadel.core.repository.WorkspaceRepository;
import io.citadel.core.security.JwtService;
import io.citadel.core.service.impl.AuthServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

  private static final CitadelProperties PROPERTIES =
      new CitadelProperties(
          new CitadelProperties.Cors(List.of("http://localhost:3000")),
          new CitadelProperties.Jwt("unit-test-secret-0123456789abcdef0123456789", 900, 7),
          new CitadelProperties.Security(false),
          "Citadel");

  @Mock private UserRepository userRepository;
  @Mock private WorkspaceRepository workspaceRepository;
  @Mock private WorkspaceMemberRepository workspaceMemberRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuditEventPublisher auditPublisher;

  private AuthServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new AuthServiceImpl(
            userRepository,
            workspaceRepository,
            workspaceMemberRepository,
            refreshTokenRepository,
            passwordEncoder,
            jwtService,
            auditPublisher,
            PROPERTIES);

    // 模拟数据库自增主键：save 时回填 id（业务代码依赖 id 非空调用 jwtService）
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User entity = inv.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(1L);
              }
              return entity;
            });
    when(workspaceRepository.save(any(Workspace.class)))
        .thenAnswer(
            inv -> {
              Workspace entity = inv.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(10L);
              }
              return entity;
            });
    when(workspaceMemberRepository.save(any(WorkspaceMember.class)))
        .thenAnswer(
            inv -> {
              WorkspaceMember entity = inv.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(100L);
              }
              return entity;
            });
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(workspaceRepository.existsBySlug(anyString())).thenReturn(false);
    when(jwtService.generateAccessToken(anyLong(), anyString(), anyLong(), any(MemberRole.class)))
        .thenReturn("access-token");
    when(jwtService.generateRefreshToken()).thenReturn("refresh-token");
    when(jwtService.hashToken("refresh-token")).thenReturn("refresh-token-hash");
  }

  private static User user(long id, String email, String hash) {
    return User.builder().id(id).email(email).passwordHash(hash).displayName("Alice").build();
  }

  private static Workspace workspace(long id) {
    return Workspace.builder().id(id).name("Alice's Workspace").slug("team-ab12cd").build();
  }

  private static WorkspaceMember membership(Workspace workspace, User user, MemberRole role) {
    return WorkspaceMember.builder()
        .id(1L)
        .workspace(workspace)
        .user(user)
        .role(role)
        .joinedAt(Instant.now())
        .build();
  }

  @Test
  @DisplayName("注册：同一事务创建用户 + 工作区 + OWNER 成员关系，并返回令牌")
  void registerCreatesTenantWithOwner() {
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");

    AuthResponse response =
        service.register(new RegisterRequest("Alice@Example.com ", "password123", " Alice "));

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.user().email()).isEqualTo("alice@example.com");
    assertThat(response.workspace().name()).isEqualTo("Alice's Workspace");
    assertThat(response.workspace().slug()).startsWith("alice-");

    var memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);
    verify(workspaceMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getRole()).isEqualTo(MemberRole.OWNER);

    // 注册发布两条审计事件：user.registered + workspace.created
    verify(auditPublisher, times(2)).publish(any(AuditedEvent.class));
    verify(auditPublisher)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                (AuditedEvent event) -> AuditActions.USER_REGISTERED.equals(event.action())));
    verify(auditPublisher)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                (AuditedEvent event) -> AuditActions.WORKSPACE_CREATED.equals(event.action())));
  }

  @Test
  @DisplayName("注册：邮箱已占用返回 409")
  void registerRejectsDuplicateEmail() {
    when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.register(new RegisterRequest("alice@example.com", "password123", "Alice")))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName("登录：邮箱不存在返回 401（与密码错误不可区分）")
  void loginWithUnknownEmailFails() {
    when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "password123")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  @Test
  @DisplayName("登录：密码错误返回 401 并记录审计事件")
  void loginWithWrongPasswordFailsAndAudits() {
    User stored = user(1L, "alice@example.com", "stored-hash");
    when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(stored));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "wrong")))
        .isInstanceOf(ApiException.class);

    verify(auditPublisher)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                (AuditedEvent event) -> AuditActions.AUTH_LOGIN_FAILED.equals(event.action())));
  }

  @Test
  @DisplayName("登录：成功返回默认工作区上下文与令牌")
  void loginSucceedsWithDefaultWorkspace() {
    User stored = user(1L, "alice@example.com", "stored-hash");
    Workspace ws = workspace(10L);
    when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(stored));
    when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
    when(workspaceMemberRepository.findFirstByUserIdOrderByJoinedAtAsc(1L))
        .thenReturn(Optional.of(membership(ws, stored, MemberRole.OWNER)));

    AuthResponse response = service.login(new LoginRequest("alice@example.com", "password123"));

    assertThat(response.workspace().id()).isEqualTo(10L);
    assertThat(response.workspace().role()).isEqualTo(MemberRole.OWNER);
    verify(auditPublisher)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                (AuditedEvent event) -> AuditActions.AUTH_LOGIN.equals(event.action())));
  }

  @Test
  @DisplayName("刷新令牌轮换：重放已吊销令牌 → 吊销该用户全部令牌并返回 401")
  void refreshReuseRevokesAllTokens() {
    RefreshToken revoked =
        RefreshToken.builder()
            .id(5L)
            .userId(1L)
            .tokenHash("hash")
            .expiresAt(Instant.now().plusSeconds(3600))
            .revokedAt(Instant.now().minusSeconds(60))
            .build();
    when(jwtService.hashToken("stolen")).thenReturn("hash");
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.refresh(new RefreshRequest("stolen")))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(((ApiException) ex).status()).isEqualTo(HttpStatus.UNAUTHORIZED));

    verify(refreshTokenRepository).revokeAllActiveByUserId(eq(1L), any(Instant.class));
    verify(auditPublisher)
        .publish(
            org.mockito.ArgumentMatchers.argThat(
                (AuditedEvent event) ->
                    AuditActions.AUTH_TOKEN_REUSE_DETECTED.equals(event.action())));
  }

  @Test
  @DisplayName("登出：吊销对应刷新令牌（幂等，未知令牌不抛错）")
  void logoutRevokesToken() {
    when(jwtService.hashToken("rt")).thenReturn("hash");

    service.logout(new RefreshRequest("rt")); // 未找到 → 静默

    RefreshToken active =
        RefreshToken.builder()
            .id(9L)
            .userId(1L)
            .tokenHash("hash")
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(active));

    service.logout(new RefreshRequest("rt"));

    assertThat(active.getRevokedAt()).isNotNull();
    verify(refreshTokenRepository).save(active);
  }
}
