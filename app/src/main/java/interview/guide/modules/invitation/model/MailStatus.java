package interview.guide.modules.invitation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 邀约邮件投递状态（Redis Stream 异步任务）
 */
@Getter
@AllArgsConstructor
public enum MailStatus {

  PENDING("待发送"),
  SENDING("发送中"),
  SENT("已发送"),
  FAILED("发送失败");

  private final String displayName;
}
