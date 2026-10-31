package interview.guide.modules.engineer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 准入流程审计动作
 */
@Getter
@AllArgsConstructor
public enum AuditAction {

  SUBMITTED("提交资料"),
  RESUBMITTED("重新提交"),
  APPROVED("审核通过"),
  REJECTED("审核驳回"),
  INVITATION_SENT("发送面试邀约"),
  INTERVIEW_CONFIRMED("候选人确认面试"),
  AI_SCORED("AI 评分完成"),
  HUMAN_APPROVED("人工复核通过"),
  HUMAN_REJECTED("人工复核驳回");

  private final String displayName;
}
