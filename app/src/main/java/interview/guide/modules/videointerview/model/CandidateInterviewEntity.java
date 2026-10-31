package interview.guide.modules.videointerview.model;

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
 * 候选人视频面试会话
 *
 * 与邀约一一对应；面试问题在创建时固化；答卷与视频归档信息汇于此
 */
@Getter
@Setter
@Entity
@Table(name = "candidate_interviews")
public class CandidateInterviewEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "invitation_id", nullable = false, unique = true)
  private Long invitationId;

  /** 邀约令牌冗余存储，候选人侧查询键 */
  @Column(name = "token", nullable = false, length = 64)
  private String token;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private CandidateInterviewStatus status = CandidateInterviewStatus.CREATED;

  /** 面试问题清单（JSON 数组） */
  @Column(name = "questions", columnDefinition = "TEXT")
  private String questions;

  @Column(name = "video_file_key", length = 256)
  private String videoFileKey;

  @Column(name = "video_file_name", length = 256)
  private String videoFileName;

  @Column(name = "video_file_size")
  private Long videoFileSize;

  @Column(name = "video_hash", length = 64)
  private String videoHash;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  /** 答卷（JSON 数组：question + answer） */
  @Column(name = "answers", columnDefinition = "TEXT")
  private String answers;

  // ===== AI 评分 =====

  @Enumerated(EnumType.STRING)
  @Column(name = "ai_score_status", nullable = false, length = 16)
  private AiScoreStatus aiScoreStatus = AiScoreStatus.NOT_STARTED;

  /** Agent 评分报告（JSON） */
  @Column(name = "ai_score", columnDefinition = "TEXT")
  private String aiScore;

  @Column(name = "ai_score_error", columnDefinition = "TEXT")
  private String aiScoreError;

  // ===== 人工复核 =====

  @Enumerated(EnumType.STRING)
  @Column(name = "human_decision", nullable = false, length = 16)
  private HumanDecision humanDecision = HumanDecision.PENDING;

  @Column(name = "human_comment", columnDefinition = "TEXT")
  private String humanComment;

  @Column(name = "human_reviewed_by")
  private Long humanReviewedBy;

  @Column(name = "human_reviewed_by_name", length = 64)
  private String humanReviewedByName;

  @Column(name = "human_reviewed_at")
  private LocalDateTime humanReviewedAt;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

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
