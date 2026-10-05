package io.citadel.core.audit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 审计事件发布器。业务代码依赖它而非 ApplicationEventPublisher， 便于 F4 阶段替换为异步/ Outbox 实现而不触碰业务代码。 */
@Component
public class AuditEventPublisher {

  private final ApplicationEventPublisher eventPublisher;

  public AuditEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void publish(AuditedEvent event) {
    eventPublisher.publishEvent(event);
  }
}
