package interview.guide.modules.invitation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 面试邀约实体
 *
 * token 是候选人侧唯一凭证（邮件链接，免登录访问）；
 * 邮件经 Redis Stream 异步发送，投递状态与重试次数字段对齐底座异步模板
 */
@Getter
@Setter
@Entity
@Table(name = "interview_invitations")
public class InterviewInvitationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "engineer_id", nullable = false)
  private Long engineerId;

  @Column(name = "token", nullable = false, unique = true, length = 64)
  private String token;

  /** 预约面试时间 */
  @Column(name = "scheduled_time", nullable = false)
  private LocalDateTime scheduledTime;

  /** 面试岗位说明（默认取工程师应聘岗位，可覆盖） */
  @Column(name = "position", length = 128)
  private String position;

  /** 自定义面试问题（JSON 数组，空则用平台默认题） */
  @Column(name = "questions", columnDefinition = "TEXT")
  private String questions;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private InvitationStatus status = InvitationStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "mail_status", nullable = false, length = 16)
  private MailStatus mailStatus = MailStatus.PENDING;

  @Column(name = "mail_retry_count", nullable = false)
  private int mailRetryCount = 0;

  @Column(name = "mail_error", columnDefinition = "TEXT")
  private String mailError;

  @Column(name = "mail_sent_at")
  private LocalDateTime mailSentAt;

  @Column(name = "invited_email", nullable = false, length = 128)
  private String invitedEmail;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_by_name", length = 64)
  private String createdByName;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @jakarta.persistence.PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @jakarta.persistence.PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
