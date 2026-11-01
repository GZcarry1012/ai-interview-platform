package interview.guide.modules.videointerview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 视觉分析状态
 */
@Getter
@AllArgsConstructor
public enum VisualStatus {

  NOT_STARTED("未分析"),
  ANALYZING("分析中"),
  COMPLETED("分析完成"),
  SKIPPED("已跳过"),
  FAILED("分析失败");

  private final String displayName;
}
