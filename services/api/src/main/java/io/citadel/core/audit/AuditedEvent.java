package io.citadel.core.audit;

/**
 * 领域审计事件（Spring ApplicationEvent 载荷）。
 *
 * @param workspaceId 租户（可为空：如用户注册发生在加入租户之前）
 * @param actorId 操作者
 * @param action 见 {@link AuditActions}
 * @param resource 受影响资源标识（如 "user:42"）
 * @param detail 补充信息（当前为纯文本，F4 升级为结构化 JSON）
 */
public record AuditedEvent(
    Long workspaceId, Long actorId, String action, String resource, String detail) {}
