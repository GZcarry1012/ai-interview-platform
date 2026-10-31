package interview.guide.modules.engineer.dto;

import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.model.EngineerStatus;
import java.time.LocalDateTime;

/**
 * 工程师列表项（不含简历全文）
 */
public record EngineerListItemDTO(
    Long id,
    String name,
    String email,
    String phone,
    String position,
    Integer experienceYears,
    String education,
    EngineerStatus status,
    String statusDisplayName,
    String resumeFileName,
    Long resumeFileSize,
    String reviewComment,
    String reviewedByName,
    LocalDateTime reviewedAt,
    String createdByName,
    String createdAt,
    boolean invitationSent
) {

  public static EngineerListItemDTO from(EngineerEntity entity, boolean invitationSent) {
    return new EngineerListItemDTO(
        entity.getId(),
        entity.getName(),
        entity.getEmail(),
        entity.getPhone(),
        entity.getPosition(),
        entity.getExperienceYears(),
        entity.getEducation(),
        entity.getStatus(),
        entity.getStatus() != null ? entity.getStatus().getDisplayName() : null,
        entity.getResumeFileName(),
        entity.getResumeFileSize(),
        entity.getReviewComment(),
        entity.getReviewedByName(),
        entity.getReviewedAt(),
        entity.getCreatedByName(),
        entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
        invitationSent
    );
  }

  /** 不可变拷贝：标记是否已发送面试邀约（供列表标签） */
  public EngineerListItemDTO withInvitationSent(boolean sent) {
    return new EngineerListItemDTO(id, name, email, phone, position, experienceYears, education,
        status, statusDisplayName, resumeFileName, resumeFileSize, reviewComment,
        reviewedByName, reviewedAt, createdByName, createdAt, sent);
  }
}
