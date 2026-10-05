package io.citadel.core.audit;

import io.citadel.core.entity.AuditEvent;
import io.citadel.core.repository.AuditEventRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计事件落库监听器。
 *
 * <p>当前为同步、同事务写库（业务回滚则审计回滚，保证一致性）； F4 阶段按 PLAN 切换为 @Async + REQUIRES_NEW（业务失败不丢审计）。
 */
@Component
public class AuditEventListener {

  private final AuditEventRepository auditEventRepository;

  public AuditEventListener(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @EventListener
  @Transactional(propagation = Propagation.REQUIRED)
  public void on(AuditedEvent event) {
    auditEventRepository.save(
        AuditEvent.builder()
            .workspaceId(event.workspaceId())
            .actorId(event.actorId())
            .action(event.action())
            .resource(event.resource())
            .detail(event.detail())
            .build());
  }
}
