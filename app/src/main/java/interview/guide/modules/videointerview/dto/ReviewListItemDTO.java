package interview.guide.modules.videointerview.dto;

import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.model.AiScoreStatus;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.HumanDecision;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工复核工作台 DTO
 */
public record ReviewListItemDTO(
    Long id,
    Long engineerId,
    String engineerName,
    String position,
    String status,
    Integer durationSeconds,
    AiScoreStatus aiScoreStatus,
    String aiScoreStatusDisplayName,
    boolean aiScoreReady,
    Integer aiOverallScore,
    String aiRecommendation,
    HumanDecision humanDecision,
    String humanDecisionDisplayName,
    String humanComment,
    String humanReviewedByName,
    LocalDateTime humanReviewedAt,
    LocalDateTime submittedAt
) {

  public static ReviewListItemDTO from(CandidateInterviewEntity entity, String engineerName,
      String position, AgentScoreReport report) {
    return new ReviewListItemDTO(
        entity.getId(),
        entity.getInvitationId(),
        engineerName,
        position,
        entity.getStatus().name(),
        entity.getDurationSeconds(),
        entity.getAiScoreStatus(),
        entity.getAiScoreStatus().getDisplayName(),
        entity.getAiScoreStatus() == AiScoreStatus.COMPLETED && report != null,
        report != null ? report.overallScore() : null,
        report != null ? report.recommendation() : null,
        entity.getHumanDecision(),
        entity.getHumanDecision().getDisplayName(),
        entity.getHumanComment(),
        entity.getHumanReviewedByName(),
        entity.getHumanReviewedAt(),
        entity.getSubmittedAt()
    );
  }
}
