package interview.guide.modules.invitation.dto;

import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.model.InvitationStatus;
import java.time.LocalDateTime;

/**
 * 候选人视角的邀约信息（公开接口，最小字段集）
 */
public record InvitationTokenDTO(
    String engineerName,
    String position,
    LocalDateTime scheduledTime,
    InvitationStatus status,
    String statusDisplayName,
    LocalDateTime confirmedAt
) {

  public static InvitationTokenDTO from(InterviewInvitationEntity entity, String engineerName) {
    return new InvitationTokenDTO(
        engineerName,
        entity.getPosition(),
        entity.getScheduledTime(),
        entity.getStatus(),
        entity.getStatus() != null ? entity.getStatus().getDisplayName() : null,
        entity.getConfirmedAt()
    );
  }
}
