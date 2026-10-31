package interview.guide.modules.videointerview.dto;

import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.model.AiScoreStatus;
import interview.guide.modules.videointerview.model.HumanDecision;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 复核详情：视频地址 + 答卷 + AI 报告 + 人工结论
 */
public record ReviewDetailDTO(
    Long id,
    String engineerName,
    String position,
    String videoStreamUrl,
    Long videoFileSize,
    Integer durationSeconds,
    List<AnswerItem> answers,
    AiScoreStatus aiScoreStatus,
    AgentScoreReport aiScore,
    String aiScoreError,
    HumanDecision humanDecision,
    String humanComment,
    String humanReviewedByName,
    LocalDateTime humanReviewedAt,
    LocalDateTime submittedAt
) {

  public record AnswerItem(int index, String question, String answer) {
  }
}
