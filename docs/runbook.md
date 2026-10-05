# Citadel 部署与运维手册（Runbook）

目标：从零到「云上可访问」，全部使用免费额度。预计 30–60 分钟（首次开通账号除外）。

## 0. 账号与前置

| 需要 | 说明 |
|---|---|
| GitHub 账号 | public 仓库 → Actions 免费无限分钟 + CodeQL 免费 |
| Neon 账号 | <https://neon.tech> 免费档 |
| Google Cloud 账号 | 新账号有 $300 试用金；本项目只用常驻免费额度，不会产生扣费（见 §6 防扣费） |
| Vercel 账号 | Hobby 免费档（非商业用途） |

---

## 1. 数据库：Neon

1. 注册后创建 Project，区域选 **ap-southeast-1（新加坡）**，Postgres 16；
2. Project Dashboard → **Connection Details**：
   - 数据库下拉选 `neondb`（或自建 `citadel`）；
   - 连接方式选 **Pooled connection**（走内置 PgBouncer）；
   - 复制连接串，形如 `postgresql://user:pass@ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`；
3. 转成 JDBC 格式写入本地 `.env`：
   ```
   DATABASE_URL=jdbc:postgresql://ep-xxx-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   DATABASE_USERNAME=neondb_owner
   DATABASE_PASSWORD=<密码>
   JWT_SECRET=$(openssl rand -base64 48)
   ```
4. 创建 **dev 分支**（Branches → Create branch，命名 `dev`）：本地开发与冒烟测试连这个分支，
   与 `main`（prod）数据隔离；分支各有独立连接串，同样取 Pooled 连接。

> 本地 `make dev-api` 首次启动时 Flyway 会自动建表（V1 基线）。

---

## 2. 后端：Google Cloud Run

### 2.1 项目与 API

```bash
export PROJECT_ID=<你的项目ID>
gcloud config set project $PROJECT_ID
gcloud services enable run.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com iamcredentials.googleapis.com
```

### 2.2 Artifact Registry 镜像仓（免费 0.5GB）

```bash
gcloud artifacts repositories create citadel \
  --repository-format=docker --location=us-central1
```

### 2.3 Secret Manager（免费 6 个版本）

```bash
printf '%s' "<prod 的 JDBC URL>" | gcloud secrets create citadel-db-url --data-file=-
printf '%s' "neondb_owner"                | gcloud secrets create citadel-db-user --data-file=-
printf '%s' "<prod 密码>"                  | gcloud secrets create citadel-db-pass --data-file=-
printf '%s' "$(openssl rand -base64 48)"  | gcloud secrets create citadel-jwt-secret --data-file=-
printf '%s' "https://<你的应用域名>"        | gcloud secrets create citadel-cors-origins --data-file=-
```

> JWT 密钥务必与本地/冒烟环境不同；轮换时创建新版本（`:latest` 自动指向最新）。

### 2.4 部署服务账号 + Workload Identity Federation（免长期密钥）

```bash
# 服务账号（部署用）
gcloud iam service-accounts create citadel-deployer

# 授权：写镜像仓 / 管理 Cloud Run / 读 Secret / 扮演 SA
for role in roles/artifactregistry.writer roles/run.admin roles/secretmanager.secretAccessor; do
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:citadel-deployer@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="$role"
done
gcloud iam service-accounts add-iam-policy-binding citadel-deployer@$PROJECT_ID.iam.gserviceaccount.com \
  --member="serviceAccount:citadel-deployer@$PROJECT_ID.iam.gserviceaccount.com" --role="roles/iam.serviceAccountUser"

# 运行时服务账号（Cloud Run 服务身份）
gcloud iam service-accounts create citadel-runtime
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:citadel-runtime@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
gcloud run services describe --format 'value(spec.template.spec.serviceAccountName)' 2>/dev/null || true

# WIF Pool + Provider（GitHub OIDC）
gcloud iam workload-identity-pools create github --location=global
gcloud iam workload-identity-pools providers create-oidc github-oidc \
  --location=global --workload-identity-pool=github \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '<你的GitHub用户名>/citadel-core'"

gcloud iam service-accounts add-iam-policy-binding citadel-deployer@$PROJECT_ID.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/<项目数字ID>/locations/global/workloadIdentityPools/github/attribute.repository/<你的GitHub用户名>/citadel-core"
```

### 2.5 GitHub 侧配置

仓库 Settings → Environments：

- **`production`**（deploy-api 用）Variables：
  - `GCP_PROJECT_ID` = 项目 ID
  - `GCP_WIF_PROVIDER` = `projects/<项目数字ID>/locations/global/workloadIdentityPools/github/providers/github-oidc`
  - `GCP_DEPLOY_SA` = `citadel-deployer@<项目ID>.iam.gserviceaccount.com`
- **`smoke`**（smoke workflow 用）Secrets：Neon **dev 分支** 的
  `DATABASE_URL / DATABASE_USERNAME / DATABASE_PASSWORD / JWT_SECRET`

### 2.6 首次部署

Actions → **deploy-api** → Run workflow。产物：`https://citadel-api-<hash>-el.a.run.app`。

> 若首次部署报运行时 Secret 权限错误，为 Cloud Run 指定运行时 SA（一次性）：
> `gcloud run services update citadel-api --region us-central1 --service-account=citadel-runtime@$PROJECT_ID.iam.gserviceaccount.com`

---

## 3. 前端：Vercel

1. Vercel → Add New Project → 导入 GitHub 仓库（**Root Directory 设为 `apps/web`**）；
2. Framework 自动识别 Next.js；Build 用默认（`pnpm build`，需在设置里把 Package Manager 指到 pnpm 或保持自动）；
3. 环境变量：`NEXT_PUBLIC_API_BASE_URL = https://citadel-api-xxx-xx.a.run.app`；
4. Deploy；得到 `https://<你的项目>.vercel.app`；
5. 回写 CORS：更新 Secret Manager 的 `citadel-cors-origins` 为该域名，然后重跑 deploy-api（或新版本 secret 后重启服务）。

---

## 4. 验证清单

- [ ] `curl https://<api>/actuator/health` → `{"status":"UP"}`
- [ ] 打开前端 → 注册新账号 → 自动进入工作台并显示默认工作区（OWNER）
- [ ] 退出登录 → 重放旧 refresh token（可选，用 curl）→ 401
- [ ] Actions：web-ci / api-ci 绿；smoke 手动触发通过
- [ ] Neon 控制台：`users` / `workspaces` / `workspace_members` / `audit_events` 有数据

## 5. 常见问题

| 现象 | 处置 |
|---|---|
| 首次请求慢 2–5s | Cloud Run 缩容到 0 的冷启动，属正常；演示前先 curl 预热 |
| Flyway 报 checksum 不一致 | 迁移文件一旦合入 main 不可修改，只能新增 `V<下一号>__xxx.sql` |
| Neon 连接数告警 | 确认用的是 **Pooled** 连接串；Hikari 池上限保持 ≤10 |
| vercel.app 大陆访问不稳 | 绑定自定义域名，或前端也走 Cloud Run（Dockerfile 已备，替换 deploy workflow 即可） |
| 401 但令牌有效 | 检查 CORS（浏览器）与 Authorization 头；prod 的 CORS secret 是否包含前端域名 |

## 6. 防扣费提醒

- GCP 免费层依赖常驻额度（Cloud Run 200 万请求/月、AR 0.5GB、Secret Manager 6 版本）；
  建议在 Billing → Budgets 设 0 元预算告警；
- 不要开启 Cloud Run `min-instances ≥ 1`（会产生常驻计费）；
- Neon 免费档 100 计算小时/月，作品集流量难以触达上限。

## 7. 安全基线（生产加固清单，迭代项）

- [ ] 为生产数据库账号收敛权限：`audit_events` 仅 `INSERT/SELECT`（append-only 强约束）；
- [ ] 登录接口限流（bucket4j 或 Cloud Armor 免费档）；
- [ ] JWT 密钥季度轮换（Secret Manager 新版本 + 滚动重启）；
- [ ] 依赖漏洞告警（Dependabot 已启用）与 CodeQL 告警处理 SLA。
