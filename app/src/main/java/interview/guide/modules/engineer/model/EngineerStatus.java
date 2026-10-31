package interview.guide.modules.engineer.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工程师准入状态
 *
 * 待审核 → 审核通过（可发送面试邀约） / 审核驳回（可修改后重新提交）
 */
@Getter
@AllArgsConstructor
public enum EngineerStatus {

  PENDING_REVIEW("待审核"),
  APPROVED("审核通过"),
  REJECTED("已驳回");

  private final String displayName;
}
