package io.citadel.core.audit;

/**
 * 领域审计动作字典（F4 在此之上扩展分页查询 API 与后台界面）。
 *
 * <p>命名规范：&lt;domain&gt;.&lt;event&gt;，全小写。
 */
public final class AuditActions {

  public static final String USER_REGISTERED = "user.registered";
  public static final String WORKSPACE_CREATED = "workspace.created";
  public static final String AUTH_LOGIN = "auth.login";
  public static final String AUTH_LOGIN_FAILED = "auth.login_failed";
  public static final String AUTH_REFRESH = "auth.refresh";
  public static final String AUTH_LOGOUT = "auth.logout";
  public static final String AUTH_TOKEN_REUSE_DETECTED = "auth.token_reuse_detected";

  private AuditActions() {}
}
