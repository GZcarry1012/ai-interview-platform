package interview.guide.modules.invitation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 邀约状态
 */
@Getter
@AllArgsConstructor
public enum InvitationStatus {

  PENDING("待确认"),
  CONFIRMED("已确认"),
  CANCELLED("已取消");

  private final String displayName;
}
