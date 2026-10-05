## 变更说明

<!-- 做了什么、为什么 -->

## 自检清单

- [ ] 已通过 `make lint`（前端 ESLint/Prettier + 后端 Spotless）
- [ ] 已通过 `make test`（本机无需 Docker 的单元测试）
- [ ] 涉及数据库结构变更时：新增 Flyway 迁移（`V<n>__xxx.sql`），且遵循 expand-contract 前向兼容规范
- [ ] 涉及业务表时：新表/查询带 `workspace_id` 租户过滤（防跨租户越权）
- [ ] 涉及权限/角色变更时：已补充权限变更审计事件
- [ ] 无任何密钥/凭据进入仓库（一律走环境变量或 Secret Manager）

## 关联

<!-- 关联 issue / 计划条目（如 F1 多租户防护） -->
