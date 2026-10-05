SHELL := /bin/bash

# macOS: 自动定位 JDK 21；其它环境请自行 export JAVA_HOME
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null || echo $(JAVA_HOME))

.DEFAULT_GOAL := help
.PHONY: help dev-api dev-web test-api test-web test lint lint-api build-api compose-up compose-down clean

help: ## 列出可用目标
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

dev-api: ## 本地运行后端（无需 Docker，读取仓库根目录 .env）
	@bash -c 'set -a; [ -f .env ] && source .env; set +a; cd services/api && JAVA_HOME="$(JAVA_HOME)" ./mvnw spring-boot:run'

dev-web: ## 本地运行前端开发服务器（热更新）
	pnpm --filter @citadel/web dev

test-api: ## 后端单元测试（无需 Docker）
	cd services/api && JAVA_HOME="$(JAVA_HOME)" ./mvnw test

test-web: ## 前端单元测试
	pnpm --filter @citadel/web test

test: test-api test-web ## 全部单元测试

lint: ## 全量静态检查（前端 ESLint+Prettier / 后端 Spotless）
	pnpm lint
	$(MAKE) lint-api

lint-api: ## 后端 Spotless 检查
	cd services/api && JAVA_HOME="$(JAVA_HOME)" ./mvnw -q spotless:check

build-api: ## 后端打包（跳过测试）
	cd services/api && JAVA_HOME="$(JAVA_HOME)" ./mvnw -q -DskipTests package

compose-up: ## Docker Compose 全栈启动（需 Docker，连接 Neon）
	docker compose up --build -d

compose-down: ## 停止并清理 Compose
	docker compose down --remove-orphans

clean: ## 清理构建产物
	cd services/api && JAVA_HOME="$(JAVA_HOME)" ./mvnw -q clean
	rm -rf apps/web/.next apps/web/next-env.d.ts
