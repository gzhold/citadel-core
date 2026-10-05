package io.citadel.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** append-only 审计事件（F4）。应用层不提供任何更新/删除途径； 生产数据库账号对本表仅授予 INSERT/SELECT（见 docs/runbook.md）。 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workspace_id")
  private Long workspaceId;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(length = 100)
  private String resource;

  @Column(columnDefinition = "text")
  private String detail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }
}
