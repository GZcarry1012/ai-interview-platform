package interview.guide.modules.engineer.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.security.UserPrincipal;
import interview.guide.infrastructure.file.DocumentParseService;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.engineer.dto.EngineerDetailDTO;
import interview.guide.modules.engineer.dto.EngineerListItemDTO;
import interview.guide.modules.engineer.dto.EngineerSaveRequest;
import interview.guide.modules.engineer.model.AuditAction;
import interview.guide.modules.engineer.model.EngineerAuditLogEntity;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.model.EngineerStatus;
import interview.guide.modules.engineer.repository.EngineerAuditLogRepository;
import interview.guide.modules.engineer.repository.EngineerRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工程师准入服务
 *
 * 合作伙伴业务负责人录入/维护工程师资料，业务审核人执行审核；
 * 组织隔离：PARTNER_ADMIN 仅能访问本组织工程师，REVIEWER/ADMIN 可访问全部
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EngineerService {

  private final EngineerRepository engineerRepository;
  private final EngineerAuditLogRepository auditLogRepository;
  private final FileStorageService fileStorageService;
  private final DocumentParseService documentParseService;

  /**
   * 录入工程师资料（含简历上传与解析）
   */
  @Transactional
  public EngineerListItemDTO create(EngineerSaveRequest request, MultipartFile resume,
      UserPrincipal operator) {
    validateDuplicate(request, null);

    String fileKey;
    String parsedText;
    try {
      fileKey = fileStorageService.uploadEngineerResume(resume);
      parsedText = documentParseService.parseContent(resume);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("工程师简历处理失败: name={}", request.name(), e);
      throw new BusinessException(ErrorCode.ENGINEER_RESUME_UPLOAD_FAILED);
    }

    EngineerEntity engineer = new EngineerEntity();
    engineer.setOrgId(requireOrgId(operator));
    engineer.setName(request.name());
    engineer.setPhone(request.phone());
    engineer.setEmail(request.email());
    engineer.setPosition(request.position());
    engineer.setExperienceYears(request.experienceYears());
    engineer.setEducation(request.education());
    engineer.setSkills(request.skills());
    engineer.setResumeFileKey(fileKey);
    engineer.setResumeFileName(resume.getOriginalFilename());
    engineer.setResumeFileSize(resume.getSize());
    engineer.setResumeText(parsedText);
    engineer.setStatus(EngineerStatus.PENDING_REVIEW);
    engineer.setCreatedBy(operator.id());
    engineer.setCreatedByName(operator.getUsername());
    EngineerEntity saved = engineerRepository.save(engineer);

    writeAudit(saved.getId(), AuditAction.SUBMITTED, operator.id(), operator.getUsername(),
        "录入工程师资料并上传简历", null);
    log.info("工程师录入完成: id={}, name={}, org={}", saved.getId(), request.name(),
        operator.orgId());
    return EngineerListItemDTO.from(saved, false);
  }

  /**
   * 驳回后修改资料并重新提交（回到待审核）
   */
  @Transactional
  public EngineerListItemDTO resubmit(Long engineerId, EngineerSaveRequest request,
      MultipartFile resume, UserPrincipal operator) {
    EngineerEntity engineer = loadAccessible(engineerId, operator);
    if (engineer.getStatus() != EngineerStatus.REJECTED) {
      throw new BusinessException(ErrorCode.ENGINEER_STATUS_INVALID,
          "仅被驳回的资料允许修改后重新提交");
    }
    validateDuplicate(request, engineerId);

    engineer.setName(request.name());
    engineer.setPhone(request.phone());
    engineer.setEmail(request.email());
    engineer.setPosition(request.position());
    engineer.setExperienceYears(request.experienceYears());
    engineer.setEducation(request.education());
    engineer.setSkills(request.skills());
    if (resume != null && !resume.isEmpty()) {
      String fileKey = fileStorageService.uploadEngineerResume(resume);
      String parsedText = documentParseService.parseContent(resume);
      fileStorageService.deleteResume(engineer.getResumeFileKey());
      engineer.setResumeFileKey(fileKey);
      engineer.setResumeFileName(resume.getOriginalFilename());
      engineer.setResumeFileSize(resume.getSize());
      engineer.setResumeText(parsedText);
    }
    engineer.setStatus(EngineerStatus.PENDING_REVIEW);
    engineer.setReviewComment(null);

    writeAudit(engineerId, AuditAction.RESUBMITTED, operator.id(), operator.getUsername(),
        "修改资料后重新提交审核", null);
    return EngineerListItemDTO.from(engineer, false);
  }

  /**
   * 角色感知的工程师列表：合作伙伴只见本组织，审核人/管理员见全部
   */
  @Transactional(readOnly = true)
  public List<EngineerListItemDTO> list(EngineerStatus status, UserPrincipal operator) {
    List<EngineerEntity> entities;
    if (isPartnerAdmin(operator)) {
      entities = status == null
          ? engineerRepository.findByOrgIdOrderByCreatedAtDesc(operator.orgId())
          : engineerRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(operator.orgId(), status);
    } else {
      entities = status == null
          ? engineerRepository.findAll()
          : engineerRepository.findByStatusOrderByCreatedAtDesc(status);
    }
    if (entities.isEmpty()) {
      return List.of();
    }
    return entities.stream().map(entity -> EngineerListItemDTO.from(entity, false))
        .collect(Collectors.toList());
  }

  /**
   * 详情（含审计日志），同样受组织隔离约束
   */
  @Transactional(readOnly = true)
  public EngineerDetailDTO detail(Long engineerId, UserPrincipal operator) {
    EngineerEntity engineer = loadAccessible(engineerId, operator);
    List<EngineerDetailDTO.AuditLogItem> logs = auditLogRepository
        .findByEngineerIdOrderByCreatedAtDesc(engineerId).stream()
        .map(logEntity -> new EngineerDetailDTO.AuditLogItem(
            logEntity.getId(),
            logEntity.getAction(),
            logEntity.getAction() != null ? logEntity.getAction().getDisplayName() : null,
            logEntity.getOperatorName(),
            logEntity.getOperatorSource(),
            logEntity.getComment(),
            logEntity.getCreatedAt()))
        .collect(Collectors.toList());
    return EngineerDetailDTO.from(engineer, fileStorageService.getFileUrl(
        engineer.getResumeFileKey()), logs);
  }

  /**
   * 审核通过
   */
  @Transactional
  public EngineerListItemDTO approve(Long engineerId, String comment, UserPrincipal operator) {
    EngineerEntity engineer = loadForReview(engineerId, operator);
    engineer.setStatus(EngineerStatus.APPROVED);
    engineer.setReviewComment(comment);
    engineer.setReviewedBy(operator.id());
    engineer.setReviewedByName(operator.getUsername());
    engineer.setReviewedAt(java.time.LocalDateTime.now());
    writeAudit(engineerId, AuditAction.APPROVED, operator.id(), operator.getUsername(),
        comment, null);
    log.info("工程师审核通过: id={}, reviewer={}", engineerId, operator.getUsername());
    return EngineerListItemDTO.from(engineer, false);
  }

  /**
   * 审核驳回
   */
  @Transactional
  public EngineerListItemDTO reject(Long engineerId, String comment, UserPrincipal operator) {
    EngineerEntity engineer = loadForReview(engineerId, operator);
    engineer.setStatus(EngineerStatus.REJECTED);
    engineer.setReviewComment(comment);
    engineer.setReviewedBy(operator.id());
    engineer.setReviewedByName(operator.getUsername());
    engineer.setReviewedAt(java.time.LocalDateTime.now());
    writeAudit(engineerId, AuditAction.REJECTED, operator.id(), operator.getUsername(),
        comment, null);
    log.info("工程师审核驳回: id={}, reviewer={}", engineerId, operator.getUsername());
    return EngineerListItemDTO.from(engineer, false);
  }

  public boolean isPartnerAdmin(UserPrincipal operator) {
    return "PARTNER_ADMIN".equals(operator.role());
  }

  /**
   * 记录跨模块审计事件（邀约发送、候选人确认等），来源标记供区分候选人侧操作
   */
  @Transactional
  public void recordExternalAudit(Long engineerId, AuditAction action, String operatorName,
      String operatorSource, String comment) {
    writeAudit(engineerId, action, null, operatorName, comment, operatorSource);
  }

  /**
   * 校验邮箱/手机号未被他用
   */
  private void validateDuplicate(EngineerSaveRequest request, Long selfId) {
    engineerRepository.findByEmail(request.email())
        .ifPresent(existing -> {
          if (!existing.getId().equals(selfId)) {
            throw new BusinessException(ErrorCode.ENGINEER_DUPLICATE);
          }
        });
    if (request.phone() != null && !request.phone().isBlank()) {
      engineerRepository.findByPhone(request.phone())
          .ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
              throw new BusinessException(ErrorCode.ENGINEER_DUPLICATE);
            }
          });
    }
  }

  private EngineerEntity loadAccessible(Long engineerId, UserPrincipal operator) {
    EngineerEntity engineer = engineerRepository.findById(engineerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));
    if (isPartnerAdmin(operator) && !engineer.getOrgId().equals(operator.orgId())) {
      throw new BusinessException(ErrorCode.ENGINEER_NO_PERMISSION);
    }
    return engineer;
  }

  private EngineerEntity loadForReview(Long engineerId, UserPrincipal operator) {
    EngineerEntity engineer = engineerRepository.findById(engineerId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));
    if (engineer.getStatus() != EngineerStatus.PENDING_REVIEW) {
      throw new BusinessException(ErrorCode.ENGINEER_STATUS_INVALID);
    }
    return engineer;
  }

  private Long requireOrgId(UserPrincipal operator) {
    if (operator.orgId() == null) {
      throw new BusinessException(ErrorCode.ENGINEER_NO_PERMISSION,
          "当前账号未绑定组织，无法录入工程师");
    }
    return operator.orgId();
  }

  private void writeAudit(Long engineerId, AuditAction action, Long operatorId,
      String operatorName, String comment, String operatorSource) {
    EngineerAuditLogEntity auditLog = new EngineerAuditLogEntity();
    auditLog.setEngineerId(engineerId);
    auditLog.setAction(action);
    auditLog.setOperatorId(operatorId);
    auditLog.setOperatorName(operatorName);
    auditLog.setOperatorSource(operatorSource);
    auditLog.setComment(comment);
    auditLogRepository.save(auditLog);
  }
}
