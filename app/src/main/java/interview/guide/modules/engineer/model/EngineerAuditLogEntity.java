package interview.guide.modules.engineer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 准入流程审计日志
 *
 * 记录提交/审核/邀约/确认等关键动作，支撑历史追溯与合规留档
 */
@Getter
@Setter
@Entity
@Table(name = "engineer_audit_logs", indexes = {
    @Index(name = "idx_audit_engineer", columnList = "engineer_id"),
})
public class EngineerAuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "engineer_id", nullable = false)
  private Long engineerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 32)
  private AuditAction action;

  @Column(name = "operator_id")
  private Long operatorId;

  @Column(name = "operator_name", length = 64)
  private String operatorName;

  @Column(name = "comment", columnDefinition = "TEXT")
  private String comment;

  /** 候选人侧操作时无账号，此字段标记来源（如 engineer-email） */
  @Column(name = "operator_source", length = 64)
  private String operatorSource;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @jakarta.persistence.PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
