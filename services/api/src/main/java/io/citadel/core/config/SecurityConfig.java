package io.citadel.core.config;

import io.citadel.core.security.JwtAuthenticationFilter;
import io.citadel.core.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // F2：方法级权限（@PreAuthorize）在此启用
public class SecurityConfig {

  /** 公共路径白名单（保持与 PLAN §5 对齐；prod 由 springdoc 关闭文档端点）。 */
  public static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/**",
    "/actuator/health",
    "/actuator/health/**",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CitadelProperties properties) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.cors().allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  /**
   * JwtAuthenticationFilter 同时被 Spring 注册为 Servlet Filter 会导致双重执行， 这里显式关闭 Servlet 容器自动注册，仅保留
   * Security 过滤器链中的那一份。
   */
  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
      JwtAuthenticationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  /** 嵌套配置缺失时兜底为 false（application.yml 已有默认值，此处双保险防 NPE）。 */
  private static boolean requireHttps(CitadelProperties properties) {
    return properties.security() != null && properties.security().requireHttps();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtFilter,
      RestAuthenticationEntryPoint entryPoint,
      CitadelProperties properties)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        // 按名称解析 corsConfigurationSource Bean：容器内还有 MvcHandlerMappingIntrospector
        // 同样实现了 CorsConfigurationSource，按类型注入会产生歧义
        .cors(Customizer.withDefaults())
        // 传输安全：prod（requireHttps=true）强制 HTTPS 通道，直连 HTTP 由 Spring Security 302
        // 重定向到 HTTPS；密码等敏感字段因此只会在 TLS 加密通道中传输。本地开发允许 loopback HTTP
        .requiresChannel(
            rc -> {
              if (requireHttps(properties)) {
                rc.anyRequest().requiresSecure();
              }
            })
        // HSTS：仅在 HTTPS 响应上生效（浏览器忽略 HTTP 响应上的该头），本地开发零副作用
        .headers(
            h ->
                h.httpStrictTransportSecurity(
                    hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true)))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
