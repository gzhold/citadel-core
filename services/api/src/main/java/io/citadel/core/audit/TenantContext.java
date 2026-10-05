package io.citadel.core.audit;

import java.util.Optional;

/**
 * 请求级租户上下文（F1「跨租户越权防护」地基）。
 *
 * <p>JwtAuthenticationFilter 从 access token 的 wid 声明注入当前请求所属 workspace，请求结束时清理。 后续迭代（F1）将在
 * repository 层强制以此过滤所有业务查询，确保 A 租户永远查不到 B 租户的数据。
 */
public final class TenantContext {

  private static final ThreadLocal<Long> WORKSPACE_ID = new ThreadLocal<>();

  private TenantContext() {}

  public static void setWorkspaceId(Long workspaceId) {
    WORKSPACE_ID.set(workspaceId);
  }

  public static Optional<Long> currentWorkspaceId() {
    return Optional.ofNullable(WORKSPACE_ID.get());
  }

  public static void clear() {
    WORKSPACE_ID.remove();
  }
}
