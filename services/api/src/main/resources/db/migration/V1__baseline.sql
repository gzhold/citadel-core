-- =====================================================================
-- Citadel V1 baseline —— 多租户地基 schema（Flyway）
-- 规范（对齐 PLAN §5，为 F1-F6 预留）：
--   1) 所有业务表必须携带 workspace_id（租户根），唯一约束以租户为前缀；
--   2) 枚举使用 varchar + CHECK（与 Hibernate validate 兼容；如需 PG enum
--      类型按 F6 expand-contract 流程迁移）；
--   3) audit_events 为 append-only：应用层不提供 UPDATE/DELETE 途径，
--      生产数据库账号应仅授予该表 INSERT/SELECT（见 docs/runbook.md）；
--   4) 软删除列（deleted_at）在对应模块落地时按 F5 决议补充。
-- =====================================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE workspaces (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(100) NOT NULL,
    plan       VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspaces_slug UNIQUE (slug),
    CONSTRAINT ck_workspaces_plan CHECK (plan IN ('FREE', 'PRO'))
);

CREATE TABLE workspace_members (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT       NOT NULL REFERENCES workspaces (id),
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    role         VARCHAR(20)  NOT NULL,
    invited_by   BIGINT       NULL REFERENCES users (id),
    joined_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_workspace_members_ws_user UNIQUE (workspace_id, user_id),
    CONSTRAINT ck_workspace_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);
CREATE INDEX ix_workspace_members_user ON workspace_members (user_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ  NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- append-only 审计事件（F4 在此之上扩展查询 API 与异步落库）
CREATE TABLE audit_events (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT       NULL REFERENCES workspaces (id),
    actor_id     BIGINT       NULL REFERENCES users (id),
    action       VARCHAR(50)  NOT NULL,
    resource     VARCHAR(100) NULL,
    detail       TEXT         NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_events_ws_time ON audit_events (workspace_id, created_at DESC);
