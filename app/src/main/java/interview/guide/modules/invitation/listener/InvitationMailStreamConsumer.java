package interview.guide.modules.invitation.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.repository.EngineerRepository;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.model.MailStatus;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.invitation.service.InvitationMailService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

/**
 * 面试邀约邮件任务消费者
 *
 * 复用底座 Redis Stream 异步模板：自动重试 3 次，超限标记 FAILED；
 * 投递状态随任务流转 PENDING → SENDING → SENT / FAILED
 */
@Slf4j
@Component
public class InvitationMailStreamConsumer extends AbstractStreamConsumer<Long> {

  private final InterviewInvitationRepository invitationRepository;
  private final EngineerRepository engineerRepository;
  private final InvitationMailService mailService;

  public InvitationMailStreamConsumer(RedisService redisService,
      InterviewInvitationRepository invitationRepository,
      EngineerRepository engineerRepository,
      InvitationMailService mailService) {
    super(redisService);
    this.invitationRepository = invitationRepository;
    this.engineerRepository = engineerRepository;
    this.mailService = mailService;
  }

  @Override
  protected String taskDisplayName() {
    return "面试邀约邮件发送";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.INVITATION_MAIL_STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return AsyncTaskStreamConstants.INVITATION_MAIL_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.INVITATION_MAIL_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "invitation-mail-consumer";
  }

  @Override
  protected Long parsePayload(StreamMessageId messageId, Map<String, String> data) {
    String id = data.get(AsyncTaskStreamConstants.FIELD_INVITATION_ID);
    if (id == null || id.isBlank()) {
      return null;
    }
    return Long.parseLong(id);
  }

  @Override
  protected String payloadIdentifier(Long invitationId) {
    return "invitationId=" + invitationId;
  }

  @Override
  protected void markProcessing(Long invitationId) {
    invitationRepository.findById(invitationId).ifPresent(invitation -> {
      invitation.setMailStatus(MailStatus.SENDING);
      invitationRepository.save(invitation);
    });
  }

  @Override
  protected void processBusiness(Long invitationId) {
    InterviewInvitationEntity invitation = invitationRepository.findById(invitationId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
    if (invitation.getStatus() == InvitationStatus.CANCELLED) {
      return;
    }
    EngineerEntity engineer = engineerRepository.findById(invitation.getEngineerId())
        .orElseThrow(() -> new BusinessException(ErrorCode.ENGINEER_NOT_FOUND));
    mailService.sendInvitationMail(invitation, engineer);
  }

  @Override
  protected void markCompleted(Long invitationId) {
    invitationRepository.findById(invitationId).ifPresent(invitation -> {
      invitation.setMailStatus(MailStatus.SENT);
      invitation.setMailSentAt(LocalDateTime.now());
      invitation.setMailError(null);
      invitationRepository.save(invitation);
    });
  }

  @Override
  protected void markFailed(Long invitationId, String error) {
    invitationRepository.findById(invitationId).ifPresent(invitation -> {
      invitation.setMailStatus(MailStatus.FAILED);
      invitation.setMailError(error);
      invitationRepository.save(invitation);
    });
    log.error("邀约邮件最终发送失败: invitationId={}, error={}", invitationId, error);
  }

  @Override
  protected void retryMessage(Long invitationId, int retryCount) {
    invitationRepository.findById(invitationId).ifPresent(invitation -> {
      invitation.setMailStatus(MailStatus.PENDING);
      invitation.setMailRetryCount(retryCount);
      invitationRepository.save(invitation);
    });
    Map<String, String> message = new HashMap<>();
    message.put(AsyncTaskStreamConstants.FIELD_INVITATION_ID, String.valueOf(invitationId));
    message.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount));
    redisService().streamAdd(streamKey(), message, AsyncTaskStreamConstants.STREAM_MAX_LEN);
    log.info("邀约邮件任务重试入队: invitationId={}, retryCount={}", invitationId, retryCount);
  }
}
