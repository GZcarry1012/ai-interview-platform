package interview.guide.modules.videointerview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.UserPrincipal;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.engineer.model.AuditAction;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.repository.EngineerRepository;
import interview.guide.modules.engineer.service.EngineerService;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.scoring.listener.ScoringStreamProducer;
import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.dto.ReviewDetailDTO;
import interview.guide.modules.videointerview.dto.ReviewListItemDTO;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.CandidateInterviewStatus;
import interview.guide.modules.videointerview.model.AiScoreStatus;
import interview.guide.modules.videointerview.model.HumanDecision;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 人工复核工作台服务
 *
 * 查看视频/答卷/AI 评分后由业务审核人定夺；
 * AI 评分为异步任务（Redis Stream），复核动作写工程师审计日志
 */
@Slf4j
@Service
public class ReviewService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CandidateInterviewRepository interviewRepository;
  private final InterviewInvitationRepository invitationRepository;
  private final EngineerRepository engineerRepository;
  private final EngineerService engineerService;
  private final FileStorageService fileStorageService;
  private final ScoringStreamProducer scoringStreamProducer;
  private final TransactionalExecutor transactionalExecutor;

  public ReviewService(CandidateInterviewRepository interviewRepository,
      InterviewInvitationRepository invitationRepository,
      EngineerRepository engineerRepository,
      EngineerService engineerService,
      FileStorageService fileStorageService,
      ScoringStreamProducer scoringStreamProducer,
      TransactionalExecutor transactionalExecutor) {
    this.interviewRepository = interviewRepository;
    this.invitationRepository = invitationRepository;
    this.engineerRepository = engineerRepository;
    this.engineerService = engineerService;
    this.fileStorageService = fileStorageService;
    this.scoringStreamProducer = scoringStreamProducer;
    this.transactionalExecutor = transactionalExecutor;
  }

  /**
   * 已提交答卷的面试列表（评分状态与人工结论概览）
   */
  public List<ReviewListItemDTO> listSubmitted() {
    return interviewRepository.findAll().stream()
        .filter(i -> i.getStatus() == CandidateInterviewStatus.COMPLETED
            || i.getStatus() == CandidateInterviewStatus.VIDEO_UPLOADED)
        .sorted(Comparator.comparing(CandidateInterviewEntity::getSubmittedAt,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .map(this::toListItem)
        .collect(Collectors.toList());
  }

  public ReviewDetailDTO detail(Long interviewId) {
    CandidateInterviewEntity interview = load(interviewId);
    EngineerEntity engineer = loadEngineer(interview);
    return new ReviewDetailDTO(
        interview.getId(),
        engineer.getName(),
        engineer.getPosition(),
        "/api/review/interviews/" + interviewId + "/video",
        interview.getVideoFileSize(),
        interview.getDurationSeconds(),
        parseAnswers(interview.getAnswers()),
        interview.getAiScoreStatus(),
        parseScore(interview.getAiScore()),
        interview.getAiScoreError(),
        interview.getHumanDecision(),
        interview.getHumanComment(),
        interview.getHumanReviewedByName(),
        interview.getHumanReviewedAt(),
        interview.getSubmittedAt()
    );
  }

  /**
   * 触发 AI 评分（异步）
   */
  public void triggerAiScore(Long interviewId, UserPrincipal operator) {
    CandidateInterviewEntity interview = load(interviewId);
    if (interview.getStatus() != CandidateInterviewStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "候选人尚未提交答卷，无法评分");
    }
    if (interview.getAiScoreStatus() == AiScoreStatus.RUNNING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 评分正在进行中，请稍后刷新查看");
    }
    boolean queued = scoringStreamProducer.enqueue(interviewId);
    if (!queued) {
      throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "评分任务入队失败，请稍后重试");
    }
    log.info("AI 评分任务已入队: interviewId={}, operator={}", interviewId, operator.getUsername());
  }

  /**
   * 人工复核定夺
   */
  public ReviewDetailDTO humanReview(Long interviewId, HumanDecision decision, String comment,
      UserPrincipal operator) {
    if (decision == HumanDecision.PENDING) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "复核结论不能为待定");
    }
    transactionalExecutor.call(() -> {
      CandidateInterviewEntity interview = interviewRepository.findById(interviewId)
          .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
      interview.setHumanDecision(decision);
      interview.setHumanComment(comment);
      interview.setHumanReviewedBy(operator.id());
      interview.setHumanReviewedByName(operator.getUsername());
      interview.setHumanReviewedAt(LocalDateTime.now());
      interviewRepository.save(interview);
      return null;
    });
    EngineerEntity engineer = loadEngineer(load(interviewId));
    engineerService.recordExternalAudit(engineer.getId(),
        decision == HumanDecision.APPROVED ? AuditAction.HUMAN_APPROVED
            : AuditAction.HUMAN_REJECTED,
        operator.getUsername(), null, comment);
    log.info("人工复核完成: interviewId={}, decision={}, reviewer={}",
        interviewId, decision, operator.getUsername());
    return detail(interviewId);
  }

  private ReviewListItemDTO toListItem(CandidateInterviewEntity interview) {
    EngineerEntity engineer = loadEngineer(interview);
    return ReviewListItemDTO.from(interview, engineer.getName(), engineer.getPosition(),
        parseScore(interview.getAiScore()));
  }

  private CandidateInterviewEntity load(Long interviewId) {
    return interviewRepository.findById(interviewId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
  }

  /** 视频归档对象键（流式播放用）；未归档时返回空 */
  public java.util.Optional<String> videoFileKey(Long interviewId) {
    return interviewRepository.findById(interviewId)
        .map(CandidateInterviewEntity::getVideoFileKey)
        .filter(key -> key != null && !key.isBlank());
  }

  /** 视频大小（字节）；未知返回 -1 */
  public long videoFileSize(Long interviewId) {
    return interviewRepository.findById(interviewId)
        .map(i -> i.getVideoFileSize() != null ? i.getVideoFileSize() : -1L)
        .orElse(-1L);
  }

  private EngineerEntity loadEngineer(CandidateInterviewEntity interview) {
    InterviewInvitationEntity invitation = invitationRepository
        .findById(interview.getInvitationId())
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    return engineerRepository.findById(invitation.getEngineerId())
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));
  }

  private List<ReviewDetailDTO.AnswerItem> parseAnswers(String answersJson) {
    if (answersJson == null || answersJson.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(answersJson, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      log.warn("答卷解析失败: {}", e.getMessage());
      return List.of();
    }
  }

  private AgentScoreReport parseScore(String scoreJson) {
    if (scoreJson == null || scoreJson.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(scoreJson, AgentScoreReport.class);
    } catch (JsonProcessingException e) {
      log.warn("AI 评分报告解析失败: {}", e.getMessage());
      return null;
    }
  }
}
