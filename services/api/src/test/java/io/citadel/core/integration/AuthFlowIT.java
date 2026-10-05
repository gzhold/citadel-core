package io.citadel.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 认证全链路集成测试（PLAN §8 验收口径）。
 *
 * <p>仅在 CI / 有 Docker 的环境执行：./mvnw verify -Pintegration
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "citadel.jwt.secret=citadel-integration-test-secret-32bytes")
@Testcontainers
@Tag("integration")
class AuthFlowIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object body) {
    return (Map<String, Object>) body;
  }

  private Map<String, Object> register(String email) {
    var request = Map.of("email", email, "password", "password123", "displayName", "Alice");
    var response = post("/api/v1/auth/register", request);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody();
  }

  /** TestRestTemplate 的 postForEntity 不支持 ParameterizedTypeReference，统一走 exchange。 */
  private ResponseEntity<Map<String, Object>> post(String path, Map<String, ?> body) {
    return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body), JSON);
  }

  @Test
  @DisplayName("注册：创建用户 + 默认工作区 + OWNER 角色，返回令牌对")
  void registerCreatesUserWorkspaceAndOwnerRole() {
    Map<String, Object> body = register("owner@example.com");

    assertThat(body).isNotNull();
    assertThat(body.get("accessToken")).asString().isNotBlank();
    assertThat(body.get("refreshToken")).asString().isNotBlank();
    assertThat(body.get("tokenType")).isEqualTo("Bearer");

    Map<String, Object> workspace = map(body.get("workspace"));
    assertThat(workspace.get("role")).isEqualTo("OWNER");
    assertThat((String) workspace.get("slug")).contains("-");
    assertThat(map(body.get("user")).get("email")).isEqualTo("owner@example.com");
  }

  @Test
  @DisplayName("登录 → 携带 Bearer 访问 /me → 返回用户与成员关系")
  void loginThenMeRoundtrip() {
    register("me@example.com");

    var login = Map.of("email", "me@example.com", "password", "password123");
    var loginResponse = post("/api/v1/auth/login", login);
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String accessToken = (String) map(loginResponse.getBody()).get("accessToken");

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    var meResponse =
        restTemplate.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), JSON);

    assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> me = meResponse.getBody();
    assertThat(map(me.get("user")).get("email")).isEqualTo("me@example.com");
    assertThat((List<Map<String, Object>>) me.get("memberships")).hasSize(1);
    assertThat(((List<Map<String, Object>>) me.get("memberships")).get(0).get("role"))
        .isEqualTo("OWNER");
  }

  @Test
  @DisplayName("无令牌访问受保护接口返回 401 + 统一错误码")
  void meWithoutTokenReturns401() {
    var response = restTemplate.exchange("/api/v1/me", HttpMethod.GET, null, JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(map(response.getBody()).get("code")).isEqualTo("UNAUTHENTICATED");
  }

  @Test
  @DisplayName("刷新令牌轮换：旧令牌一次性使用，重放触发全部吊销")
  void refreshRotatesAndReuseRevokesEverything() {
    register("refresh@example.com");

    var login = Map.of("email", "refresh@example.com", "password", "password123");
    String firstRefresh =
        (String) map(post("/api/v1/auth/login", login).getBody()).get("refreshToken");

    // 第一次刷新：成功并轮换出新令牌
    var rotated = post("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh));
    assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
    String secondRefresh = (String) map(rotated.getBody()).get("refreshToken");
    assertThat(secondRefresh).isNotEqualTo(firstRefresh);

    // 重放旧令牌：401，并触发该用户全部令牌吊销
    var replay = post("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh));
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 新令牌也被连坐吊销：必须重新登录
    var collateral = post("/api/v1/auth/refresh", Map.of("refreshToken", secondRefresh));
    assertThat(collateral.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
