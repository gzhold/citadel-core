package io.citadel.core.security;

import io.citadel.core.audit.TenantContext;
import io.citadel.core.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer JWT 校验过滤器：验证签名 → 确认用户仍存在 → 写入 SecurityContext 与 TenantContext。
 *
 * <p>无效令牌不立即中断请求（保持匿名），由 RestAuthenticationEntryPoint 统一返回 401， 保证语义一致且不泄露失败原因。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        var claims = jwtService.parseAccessToken(header.substring(7));
        userRepository
            .findById(claims.userId())
            .ifPresent(
                user -> {
                  var authorities =
                      List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));
                  var authentication =
                      new UsernamePasswordAuthenticationToken(claims.userId(), claims, authorities);
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                  // F1 地基：请求级租户上下文，repository 层后续据此强制过滤
                  TenantContext.setWorkspaceId(claims.workspaceId());
                });
      } catch (JwtException | IllegalArgumentException ex) {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}
