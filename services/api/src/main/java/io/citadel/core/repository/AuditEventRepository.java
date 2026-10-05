package io.citadel.core.repository;

import io.citadel.core.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** 审计事件仓库：只允许 save（insert）与查询，不提供任何修改/删除语义（F4）。 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {}
