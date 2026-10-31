package interview.guide.modules.engineer.dto;

import interview.guide.modules.engineer.model.AuditAction;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.model.EngineerStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工程师详情（含审计日志）
 */
public record EngineerDetailDTO(
    Long id,
    String name,
    String email,
    String phone,
    String position,
    Integer experienceYears,
    String education,
    String skills,
    EngineerStatus status,
    String statusDisplayName,
    String resumeFileName,
    Long resumeFileSize,
    String resumeFileUrl,
    String reviewComment,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String createdByName,
    LocalDateTime createdAt,
    List<AuditLogItem> auditLogs
) {

  public record AuditLogItem(
      Long id,
      AuditAction action,
      String actionDisplayName,
      String operatorName,
      String operatorSource,
      String comment,
      LocalDateTime createdAt
  ) {
  }

  public static EngineerDetailDTO from(EngineerEntity entity, String resumeUrl,
      List<AuditLogItem> logs) {
    return new EngineerDetailDTO(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPhone(),
        entity.getPosition(),
        entity.getExperienceYears(),
        entity.getEducation(),
        entity.getSkills(),
        entity.getStatus(),
        entity.getStatus() != null ? entity.getStatus().getDisplayName() : null,
        entity.getResumeFileName(),
        entity.getResumeFileSize(),
        resumeUrl,
        entity.getReviewComment(),
        entity.getReviewedByName(),
        entity.getReviewedAt(),
        entity.getCreatedByName(),
        entity.getCreatedAt(),
        logs
    );
  }
}
