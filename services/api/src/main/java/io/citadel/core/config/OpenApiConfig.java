package io.citadel.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI citadelOpenApi(CitadelProperties properties) {
    var schemeName = "bearerAuth";
    return new OpenAPI()
        .info(
            new Info()
                .title(properties.appName() + " API")
                .version("v1")
                .description(
                    "Citadel — B2B 多租户团队协作平台（个人作品集项目）."
                        + " 认证方式：POST /api/v1/auth/login 获取 JWT 后以 Bearer 头访问受保护接口。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    schemeName,
                    new SecurityScheme()
                        .name(schemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(schemeName));
  }
}
