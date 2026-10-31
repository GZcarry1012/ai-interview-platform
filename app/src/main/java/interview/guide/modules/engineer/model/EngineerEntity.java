package interview.guide.modules.engineer.model;

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
 * 工程师实体
 *
 * 由合作伙伴业务负责人录入，业务审核人执行准入审核；
 * 简历原件存对象存储（RustFS），解析文本入库供后续 AI 评估使用
 */
@Getter
@Setter
@Entity
@Table(name = "engineers")
public class EngineerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 所属合作伙伴组织，数据隔离边界 */
  @Column(name = "org_id", nullable = false)
  private Long orgId;

  @Column(name = "name", nullable = false, length = 64)
  private String name;

  @Column(name = "phone", length = 32)
  private String phone;

  @Column(name = "email", nullable = false, length = 128)
  private String email;

  /** 应聘岗位 */
  @Column(name = "position", nullable = false, length = 64)
  private String position;

  /** 工作年限 */
  @Column(name = "experience_years")
  private Integer experienceYears;

  @Column(name = "education", length = 32)
  private String education;

  /** 技能清单（自由文本，供 AI 评估参考） */
  @Column(name = "skills", columnDefinition = "TEXT")
  private String skills;

  @Column(name = "resume_file_key", nullable = false, length = 256)
  private String resumeFileKey;

  @Column(name = "resume_file_name", length = 256)
  private String resumeFileName;

  @Column(name = "resume_file_size")
  private Long resumeFileSize;

  /** 简历解析文本（Tika），供 AI 分析与追问使用 */
  @Column(name = "resume_text", columnDefinition = "TEXT")
  private String resumeText;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private EngineerStatus status = EngineerStatus.PENDING_REVIEW;

  @Column(name = "review_comment", columnDefinition = "TEXT")
  private String reviewComment;

  @Column(name = "reviewed_by")
  private Long reviewedBy;

  @Column(name = "reviewed_by_name", length = 64)
  private String reviewedByName;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_by_name", length = 64)
  private String createdByName;

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
