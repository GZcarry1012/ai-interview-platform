package interview.guide.modules.scoring.model;

import java.util.List;

/**
 * Agent 评分报告（结构化输出）
 */
public record AgentScoreReport(
    int overallScore,
    List<DimensionScore> dimensions,
    List<String> strengths,
    List<String> risks,
    String recommendation,
    String recommendationReason,
    String summary,
    List<QuestionFeedback> questionFeedback
) {

  public record DimensionScore(String name, int score, String comment) {
  }

  public record QuestionFeedback(int index, String comment) {
  }
}
