package interview.guide.modules.videointerview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.videointerview.config.VideoUploadProperties;
import interview.guide.modules.videointerview.dto.CandidateSessionDTO;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.CandidateInterviewStatus;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 候选人面试会话服务
 *
 * 懒创建：候选人首次打开面试页时生成会话并固化问题清单
 * （邀约自定义问题优先，否则平台默认题）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateInterviewService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CandidateInterviewRepository interviewRepository;
  private final InterviewInvitationRepository invitationRepository;
  private final VideoUploadProperties properties;

  @Transactional
  public CandidateSessionDTO getSession(String token) {
    InterviewInvitationEntity invitation = loadConfirmedInvitation(token);
    CandidateInterviewEntity interview = interviewRepository
        .findByInvitationId(invitation.getId())
        .orElseGet(() -> createInterview(invitation));
    return toDTO(interview, invitation);
  }

  /**
   * 提交答卷（视频可与答卷先后提交，均完成后会话置为 COMPLETED）
   */
  @Transactional
  public CandidateSessionDTO submitAnswers(String token, String answersJson) {
    CandidateInterviewEntity interview = loadByToken(token);
    if (interview.getStatus() == CandidateInterviewStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.CANDIDATE_ALREADY_SUBMITTED);
    }
    interview.setAnswers(answersJson);
    interview.setStatus(CandidateInterviewStatus.COMPLETED);
    interview.setSubmittedAt(LocalDateTime.now());
    interviewRepository.save(interview);
    log.info("候选人答卷已提交: interviewId={}", interview.getId());
    InterviewInvitationEntity invitation = invitationRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_TOKEN_INVALID));
    return toDTO(interview, invitation);
  }

  private CandidateInterviewEntity createInterview(InterviewInvitationEntity invitation) {
    CandidateInterviewEntity interview = new CandidateInterviewEntity();
    interview.setInvitationId(invitation.getId());
    interview.setToken(invitation.getToken());
    interview.setQuestions(invitation.getQuestions() != null
        ? invitation.getQuestions() : serializeQuestions(properties.getDefaultQuestions()));
    interview.setStatus(CandidateInterviewStatus.CREATED);
    interview.setStartedAt(LocalDateTime.now());
    CandidateInterviewEntity saved = interviewRepository.save(interview);
    log.info("候选人面试会话已创建: interviewId={}, invitationId={}",
        saved.getId(), invitation.getId());
    return saved;
  }

  private CandidateSessionDTO toDTO(CandidateInterviewEntity interview,
      InterviewInvitationEntity invitation) {
    return new CandidateSessionDTO(
        interview.getStatus().name(),
        interview.getStatus().getDisplayName(),
        parseQuestions(interview.getQuestions()),
        interview.getVideoFileKey() != null,
        interview.getAnswers() != null,
        interview.getDurationSeconds(),
        interview.getSubmittedAt()
    );
  }

  private CandidateInterviewEntity loadByToken(String token) {
    return interviewRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
  }

  private InterviewInvitationEntity loadConfirmedInvitation(String token) {
    InterviewInvitationEntity invitation = invitationRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_TOKEN_INVALID));
    if (invitation.getStatus() != InvitationStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.CANDIDATE_NOT_CONFIRMED);
    }
    return invitation;
  }

  private List<String> parseQuestions(String questionsJson) {
    if (questionsJson == null || questionsJson.isBlank()) {
      return properties.getDefaultQuestions();
    }
    try {
      return MAPPER.readValue(questionsJson, new TypeReference<List<String>>() {
      });
    } catch (JsonProcessingException e) {
      log.warn("问题清单解析失败，回退默认题: {}", e.getMessage());
      return properties.getDefaultQuestions();
    }
  }

  private String serializeQuestions(List<String> questions) {
    try {
      return MAPPER.writeValueAsString(questions);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND, "问题清单序列化失败");
    }
  }
}
