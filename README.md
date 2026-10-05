# Citadel

> B2B 多租户团队协作平台 —— 个人作品集项目，与任何同名公司或组织无关。

[![web-ci](https://github.com/gaozhao/citadel-core/actions/workflows/web-ci.yml/badge.svg)](./.github/workflows/web-ci.yml)
[![api-ci](https://github.com/gaozhao/citadel-core/actions/workflows/api-ci.yml/badge.svg)](./.github/workflows/api-ci.yml)
[![codeql](https://github.com/gaozhao/citadel-core/actions/workflows/codeql.yml/badge.svg)](./.github/workflows/codeql.yml)

> badge 中的 owner/仓库名推送 GitHub 后按实际地址替换。

## 这是什么

一个**生产级工程形态**的全栈作品：Next.js 前端 + Spring Boot 后端 + PostgreSQL（Neon），
monorepo 管理，CI/CD 全自动。当前完成认证全链路（注册即建租户 / 登录 / JWT 轮换 / 受保护接口），
后续按路线图迭代组织治理能力（详见 [路线图](#路线图)）。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 `apps/web` | Next.js 15（App Router）· TypeScript strict · Tailwind CSS 4 · shadcn/ui 风格组件 · TanStack Query · Zustand · react-hook-form + zod |
| 后端 `services/api` | Java 21 · Spring Boot 3.5 · Spring Security + JWT（jjwt）· Spring Data JPA · Flyway · PostgreSQL |
| 契约 `packages/api-contract` | zod schema 单一事实来源（表单校验与响应解析共用） |
| 数据库 | Neon PostgreSQL（Serverless，scale-to-zero，免费额度） |
| 部署 | 后端 Google Cloud Run · 前端 Vercel Hobby · 镜像 Artifact Registry |
| CI/CD | GitHub Actions（lint/test/integration/smoke/deploy/CodeQL）+ Dependabot |

## 快速开始（本地开发，无需 Docker、无需本地数据库）

前置：JDK 21（`make` 会自动定位）、Node 22+、pnpm 9+。

```bash
# 1. 配置环境变量（默认连 Neon dev 分支）
cp .env.example .env
#    编辑 .env：填入 Neon Pooled connection URL / 凭据 / JWT_SECRET（openssl rand -base64 48）

# 2. 后端（Spring Boot，Flyway 自动建表）
make dev-api            # http://localhost:8080  · Swagger: /swagger-ui.html

# 3. 前端（另一个终端）
cp apps/web/.env.example apps/web/.env.local
make dev-web            # http://localhost:3000
```

常用命令见 `make help`：`test`（本机单测）/ `lint` / `build-api` / `compose-up`。

> 本机没有 Docker 也能完成日常开发与测试；集成测试（Testcontainers）与镜像构建在 CI 上执行。

## Docker Compose 全栈联调（可选）

```bash
cp .env.example .env && $EDITOR .env   # 同上
make compose-up    # web:3000 + api:8080，直连 Neon
make compose-down
```

## 仓库结构

```
citadel-core/
├── apps/web/               # Next.js 前端（@citadel/web）
├── services/api/           # Spring Boot 后端（io.citadel.core）
├── packages/api-contract/  # 前后端共享 zod 契约
├── .github/workflows/      # web-ci / api-ci / smoke / deploy-api / codeql
├── docker-compose.yml      # 全栈本地联调
├── Makefile                # 常用命令入口
└── docs/                   # architecture.md / runbook.md
```

## API 概览（v1）

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 注册并创建默认工作区（OWNER） | 否 |
| POST | `/api/v1/auth/login` | 登录，返回 access(15m) + refresh(7d) | 否 |
| POST | `/api/v1/auth/refresh` | 刷新令牌轮换（重放即吊销全部令牌） | 否 |
| POST | `/api/v1/auth/logout` | 吊销刷新令牌 | 否 |
| GET | `/api/v1/me` | 当前用户 + 所属工作区与角色 | Bearer |
| GET | `/actuator/health` | 健康检查（Cloud Run/compose 探针） | 否 |

错误响应统一为 `{ code, message, timestamp }`，机器可读错误码（如 `INVALID_CREDENTIALS`、`EMAIL_ALREADY_USED`）。

## 测试策略

| 层 | 工具 | 运行环境 |
|---|---|---|
| 后端单元测试 | JUnit 5 + Mockito（`./mvnw test`） | 本机，无 Docker |
| 后端集成测试 | Testcontainers + PostgreSQL 16（`./mvnw verify -Pintegration`） | CI（或本机有 Docker） |
| 前端单测 | Vitest（API client 刷新轮换/错误映射等） | 本机 |
| 全栈冒烟 | docker compose + 健康检查 + 认证探针（smoke workflow） | CI 手动触发 |

## 云部署

完整步骤见 **[docs/runbook.md](./docs/runbook.md)**：Neon 建库 → GCP（WIF + Secret Manager + Cloud Run）→ Vercel。

## 路线图（组织治理主线）

- [x] **F0 脚手架**：认证全链路 + 多租户地基 schema（`workspaces` 租户根 / 角色成员表 / 审计表 / 刷新令牌表）
- [ ] **F1 跨租户越权防护**：TenantContext 已就位，落地仓储层强制过滤 + 防御性集成测试（A 租户请求 B 租户一律 404）
- [ ] **F2 RBAC**：Owner/Admin/Member + 邀请令牌流程 + 权限变更审计
- [ ] **F3 订阅计费钩子**：seats 用量计量 + Stripe 测试模式 webhook（签名校验/幂等）
- [ ] **F4 审计日志**：异步落库 + 管理端分页查询 API
- [ ] **F5 软删除**：Hibernate `@SoftDelete` vs `deleted_at` 选型权衡
- [ ] **F6 数据迁移策略**：expand-contract 规范 + Neon 分支演练生产迁移
- [ ] 业务模块：看板 Boards / 工单 Issues / 评论 / 活动流 / 仪表盘

## 声明

个人作品集项目，仅用于展示工程能力，不对外提供服务；与任何同名公司、产品无关。
