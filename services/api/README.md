# citadel-api

Citadel 后端：Java 21 + Spring Boot 3.5 + PostgreSQL（Flyway 迁移，`ddl-auto: validate`）。

## 常用命令（在仓库根目录）

```bash
make dev-api     # 本地运行（读取根目录 .env，无需 Docker）
make test-api    # 单元测试（无需 Docker）
make lint-api    # Spotless 格式检查
make build-api   # 打包 target/citadel-api.jar

# 集成测试（需要 Docker，CI 上执行）
cd services/api && ./mvnw verify -Pintegration
```

## 运行配置

全部通过环境变量注入（见根目录 `.env.example`）：
`DATABASE_URL`（JDBC，Neon Pooled）/ `DATABASE_USERNAME` / `DATABASE_PASSWORD` / `JWT_SECRET`（≥32 字节）/ `CORS_ALLOWED_ORIGINS`。

## 分层与规范

```
io.citadel.core
├── controller/  REST 端点 + 全局异常处理
├── service/     业务逻辑与事务边界
├── repository/  Spring Data JPA
├── entity/      实体（workspace_id 租户前缀唯一约束规范）
├── security/    JWT 签发/校验过滤器
├── audit/       TenantContext + append-only 审计事件
└── config/      安全/CORS/OpenAPI/属性
```

- 迁移文件只增不改：`src/main/resources/db/migration/V<n>__xxx.sql`
- 业务表必须携带 `workspace_id`（防跨租户越权，见 PLAN F1）
- API 文档（dev）：`/swagger-ui.html`
