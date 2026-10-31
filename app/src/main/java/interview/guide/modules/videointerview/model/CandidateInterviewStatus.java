package interview.guide.modules.videointerview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 候选人面试会话状态
 */
@Getter
@AllArgsConstructor
public enum CandidateInterviewStatus {

  CREATED("已创建"),
  VIDEO_UPLOADED("视频已归档"),
  COMPLETED("已完成（含答卷）");

  private final String displayName;
}
