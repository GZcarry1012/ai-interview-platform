package interview.guide.modules.invitation.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.UserPrincipal;
import interview.guide.modules.engineer.model.AuditAction;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.model.EngineerStatus;
import interview.guide.modules.engineer.repository.EngineerRepository;
import interview.guide.modules.engineer.service.EngineerService;
import interview.guide.modules.invitation.config.InvitationProperties;
import interview.guide.modules.invitation.dto.InvitationDTO;
import interview.guide.modules.invitation.dto.InvitationSendRequest;
import interview.guide.modules.invitation.dto.InvitationTokenDTO;
import interview.guide.modules.invitation.listener.InvitationMailStreamProducer;
import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试邀约服务
 *
 * 仅审核通过的工程师可发起邀约；同一工程师同时只允许一条进行中的邀约；
 * 邮件通过 Redis Stream 异步投递；候选人凭 token 免登录查看与确认
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

  private final InterviewInvitationRepository invitationRepository;
  private final EngineerRepository engineerRepository;
  private final EngineerService engineerService;
  private final InvitationMailStreamProducer mailStreamProducer;
  private final InvitationProperties properties;

  /**
   * 发送面试邀约：创建邀请记录并入队邮件任务
   */
  @Transactional
  public InvitationDTO send(Long engineerId, InvitationSendRequest request,
      UserPrincipal operator) {
    EngineerEntity engineer = loadAccessible(engineerId, operator);
    if (engineer.getStatus() != EngineerStatus.APPROVED) {
      throw new BusinessException(ErrorCode.INVITATION_ENGINEER_NOT_APPROVED);
    }
    if (invitationRepository.existsByEngineerIdAndStatusNot(engineerId,
        InvitationStatus.CANCELLED)) {
      throw new BusinessException(ErrorCode.INVITATION_CREATE_FAILED, "该工程师已存在进行中的邀约");
    }

    InterviewInvitationEntity invitation = new InterviewInvitationEntity();
    invitation.setEngineerId(engineerId);
    invitation.setToken(UUID.randomUUID().toString().replace("-", ""));
    invitation.setScheduledTime(request.scheduledTime());
    invitation.setPosition(request.position() != null && !request.position().isBlank()
        ? request.position() : engineer.getPosition());
    invitation.setQuestions(request.questions() != null && !request.questions().isEmpty()
        ? serializeQuestions(request.questions()) : null);
    invitation.setInvitedEmail(engineer.getEmail());
    invitation.setCreatedBy(operator.id());
    invitation.setCreatedByName(operator.getUsername());
    InterviewInvitationEntity saved = invitationRepository.save(invitation);

    engineerService.recordExternalAudit(engineerId, AuditAction.INVITATION_SENT,
        operator.getUsername(), null,
        "发送面试邀约，预约时间 " + request.scheduledTime());

    boolean queued = mailStreamProducer.send(saved.getId());
    if (!queued) {
      saved.setMailError("邮件任务入队失败");
      invitationRepository.save(saved);
    }
    log.info("面试邀约已创建: id={}, engineerId={}, operator={}", saved.getId(), engineerId,
        operator.getUsername());
    return InvitationDTO.from(saved, engineer.getName(), buildInvitationUrl(saved.getToken()));
  }

  /**
   * 查询工程师的邀约记录（组织隔离同工程师模块）
   */
  @Transactional(readOnly = true)
  public List<InvitationDTO> listByEngineer(Long engineerId, UserPrincipal operator) {
    EngineerEntity engineer = loadAccessible(engineerId, operator);
    return invitationRepository.findByEngineerIdOrderByCreatedAtDesc(engineerId).stream()
        .map(invitation -> InvitationDTO.from(invitation, engineer.getName(),
            buildInvitationUrl(invitation.getToken())))
        .collect(Collectors.toList());
  }

  /**
   * 批量查询存在有效邀约的工程师 ID（供列表“已邀约”标签）
   */
  @Transactional(readOnly = true)
  public Set<Long> engineerIdsWithInvitations(Collection<Long> engineerIds) {
    if (engineerIds == null || engineerIds.isEmpty()) {
      return Set.of();
    }
    return invitationRepository
        .findByEngineerIdInAndStatusNot(engineerIds, InvitationStatus.CANCELLED)
        .stream()
        .map(InterviewInvitationEntity::getEngineerId)
        .collect(Collectors.toSet());
  }

  /**
   * 候选人凭 token 查看邀约（免登录）
   */
  @Transactional(readOnly = true)
  public InvitationTokenDTO getByToken(String token) {
    InterviewInvitationEntity invitation = invitationRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_TOKEN_INVALID));
    String engineerName = engineerRepository.findById(invitation.getEngineerId())
        .map(EngineerEntity::getName).orElse("");
    return InvitationTokenDTO.from(invitation, engineerName);
  }

  /**
   * 候选人凭 token 确认参加
   */
  @Transactional
  public InvitationTokenDTO confirmByToken(String token) {
    InterviewInvitationEntity invitation = invitationRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_TOKEN_INVALID));
    if (invitation.getStatus() == InvitationStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.INVITATION_ALREADY_CONFIRMED);
    }
    if (invitation.getStatus() != InvitationStatus.PENDING) {
      throw new BusinessException(ErrorCode.INVITATION_TOKEN_INVALID);
    }
    invitation.setStatus(InvitationStatus.CONFIRMED);
    invitation.setConfirmedAt(LocalDateTime.now());
    invitationRepository.save(invitation);

    engineerService.recordExternalAudit(invitation.getEngineerId(),
        AuditAction.INTERVIEW_CONFIRMED, invitation.getInvitedEmail(), "candidate",
        "候选人确认参加 " + invitation.getScheduledTime());
    log.info("候选人已确认面试: invitationId={}, engineerId={}", invitation.getId(),
        invitation.getEngineerId());

    String engineerName = engineerRepository.findById(invitation.getEngineerId())
        .map(EngineerEntity::getName).orElse("");
    return InvitationTokenDTO.from(invitation, engineerName);
  }

  private EngineerEntity loadAccessible(Long engineerId, UserPrincipal operator) {
    EngineerEntity engineer = engineerRepository.findById(engineerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));
    if ("PARTNER_ADMIN".equals(operator.role())
        && !engineer.getOrgId().equals(operator.orgId())) {
      throw new BusinessException(ErrorCode.ENGINEER_NO_PERMISSION);
    }
    return engineer;
  }

  private String buildInvitationUrl(String token) {
    return properties.getFrontendBaseUrl() + "/invitation/" + token;
  }

  private static final ObjectMapper QUESTIONS_MAPPER = new ObjectMapper();

  private String serializeQuestions(List<String> questions) {
    try {
      return QUESTIONS_MAPPER.writeValueAsString(questions);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.INVITATION_CREATE_FAILED, "面试问题序列化失败");
    }
  }
}
