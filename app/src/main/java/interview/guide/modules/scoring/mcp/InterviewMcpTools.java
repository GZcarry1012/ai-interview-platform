package interview.guide.modules.scoring.mcp;

import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.repository.EngineerRepository;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP 对外工具集
 *
 * 将面试评分能力暴露为 MCP 工具，供 Claude/Inspector 等 MCP 客户端调用：
 * 外部 Agent 可以拉取某场面试的脱敏评估报告，或查询评分标准
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewMcpTools {

  private final CandidateInterviewRepository interviewRepository;
  private final InterviewInvitationRepository invitationRepository;
  private final EngineerRepository engineerRepository;
  private final ObjectMapper objectMapper;
  private final interview.guide.modules.scoring.service.StandardTextProvider standardTextProvider;

  @Tool(description = "获取指定面试会话的 AI 评分报告（脱敏：不含简历全文与联系方式）。会话必须已完成 AI 评分")
  public String getInterviewScoreReport(
      @ToolParam(description = "面试会话 ID（candidate_interviews.id）") Long interviewId) {
    var interview = interviewRepository.findById(interviewId)
        .orElseThrow(() -> new IllegalArgumentException("面试会话不存在: " + interviewId));
    if (interview.getAiScore() == null) {
      return "{\"error\":\"该会话尚未完成 AI 评分\",\"status\":\"" + interview.getAiScoreStatus() + "\"}";
    }
    var invitation = invitationRepository.findById(interview.getInvitationId()).orElse(null);
    String engineerName = "";
    String position = "";
    if (invitation != null) {
      engineerName = engineerRepository.findById(invitation.getEngineerId())
          .map(EngineerEntity::getName).orElse("");
      position = safe(invitation.getPosition());
    }
    try {
      AgentScoreReport report = objectMapper.readValue(interview.getAiScore(),
          AgentScoreReport.class);
      return objectMapper.writeValueAsString(java.util.Map.of(
          "interviewId", interviewId,
          "candidateName", engineerName,
          "position", position,
          "overallScore", report.overallScore(),
          "recommendation", report.recommendation(),
          "summary", report.summary() == null ? "" : report.summary(),
          "dimensions", report.dimensions(),
          "risks", report.risks()
      ));
    } catch (JsonProcessingException e) {
      log.warn("MCP 评分报告序列化失败: interviewId={}", interviewId, e);
      return "{\"error\":\"report serialize failed\"}";
    }
  }

  @Tool(description = "查询面试评分标准：返回维度权重、分数区间与风险点定义")
  public String getScoringStandards() {
    return standardTextProvider.get();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
