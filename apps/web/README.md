# @citadel/web

Citadel 前端：Next.js 15（App Router）+ TypeScript strict + Tailwind CSS 4。

## 常用命令（在仓库根目录）

```bash
make dev-web     # 开发服务器 http://localhost:3000
pnpm --filter @citadel/web typecheck
pnpm --filter @citadel/web test        # vitest
pnpm --filter @citadel/web lint        # eslint
pnpm --filter @citadel/web build
```

## 环境变量

复制 `.env.example` 为 `.env.local`，必填 `NEXT_PUBLIC_API_BASE_URL`（默认 `http://localhost:8080`）。
注意 `NEXT_PUBLIC_*` 在**构建期内联**：改值后需重新 build（Docker 构建走 build arg，见根目录 Dockerfile）。

## 结构速览

- `src/api/` — fetch 封装（Bearer 注入、401 单次刷新重放）与认证接口
- `src/store/` — zustand 会话（localStorage 持久化）
- `src/app/(auth)/` — 登录/注册（公开路由组）
- `src/app/(app)/` — 受保护路由组（布局级守卫 + `/dashboard`）
- 契约位于 `packages/api-contract`（zod schema 前后端共用）
