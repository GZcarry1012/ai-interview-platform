package interview.guide.modules.videointerview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 评分任务状态
 */
@Getter
@AllArgsConstructor
public enum AiScoreStatus {

  NOT_STARTED("未评分"),
  RUNNING("评分中"),
  COMPLETED("已评分"),
  FAILED("评分失败");

  private final String displayName;
}
