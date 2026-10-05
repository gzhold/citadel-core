# syntax=docker/dockerfile:1
# 构建上下文为仓库根目录（见 docker-compose.yml 的 context: .）

FROM node:22-alpine AS base
RUN corepack enable
WORKDIR /repo

# ---- 依赖层：只拷贝 package 清单，最大化层缓存 ----
FROM base AS deps
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/api-contract/package.json packages/api-contract/
RUN pnpm install --frozen-lockfile --filter @citadel/web...

# ---- 构建层：NEXT_PUBLIC_* 在构建期内联，必须作为 ARG 传入 ----
FROM deps AS build
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
COPY . .
RUN pnpm --filter @citadel/web build

# ---- 运行层：standalone 产物 + 非 root ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0

COPY --from=build /repo/apps/web/.next/standalone ./
COPY --from=build /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build /repo/apps/web/public ./apps/web/public

USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3000/api/health | grep -q '"ok"' || exit 1

CMD ["node", "apps/web/server.js"]
