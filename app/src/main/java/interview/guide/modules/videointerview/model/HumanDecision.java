package interview.guide.modules.videointerview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人工复核结论
 */
@Getter
@AllArgsConstructor
public enum HumanDecision {

  PENDING("待复核"),
  APPROVED("复核通过"),
  REJECTED("复核不通过");

  private final String displayName;
}
