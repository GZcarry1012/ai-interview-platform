package interview.guide.modules.invitation.dto;

import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.model.MailStatus;
import java.time.LocalDateTime;

/**
 * 邀约详情（内部视角，含投递状态与可复制的邀约链接）
 */
public record InvitationDTO(
    Long id,
    Long engineerId,
    String engineerName,
    String invitedEmail,
    String position,
    LocalDateTime scheduledTime,
    InvitationStatus status,
    String statusDisplayName,
    MailStatus mailStatus,
    String mailStatusDisplayName,
    String mailError,
    LocalDateTime mailSentAt,
    LocalDateTime confirmedAt,
    LocalDateTime createdAt,
    String invitationUrl
) {

  public static InvitationDTO from(InterviewInvitationEntity entity, String engineerName,
      String invitationUrl) {
    return new InvitationDTO(
        entity.getId(),
        entity.getEngineerId(),
        engineerName,
        entity.getInvitedEmail(),
        entity.getPosition(),
        entity.getScheduledTime(),
        entity.getStatus(),
        entity.getStatus() != null ? entity.getStatus().getDisplayName() : null,
        entity.getMailStatus(),
        entity.getMailStatus() != null ? entity.getMailStatus().getDisplayName() : null,
        entity.getMailError(),
        entity.getMailSentAt(),
        entity.getConfirmedAt(),
        entity.getCreatedAt(),
        invitationUrl
    );
  }
}
