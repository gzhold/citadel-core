package io.citadel.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 应用配置（citadel.*）。所有环境差异通过环境变量注入，本类做启动期 fail-fast 校验。 */
@Validated
@ConfigurationProperties(prefix = "citadel")
public record CitadelProperties(@Valid Cors cors, @Valid Jwt jwt, String appName) {

  /** CORS 允许来源（逗号分隔 → List）。 */
  public record Cors(@NotEmpty List<String> allowedOrigins) {}

  /** JWT 签发参数。密钥至少 32 字节（HS256 要求），由 JwtService 做长度校验。 */
  public record Jwt(
      @NotBlank String secret,
      @Positive @Max(3600) long accessTtlSeconds,
      @Positive @Max(90) long refreshTtlDays) {}
}
