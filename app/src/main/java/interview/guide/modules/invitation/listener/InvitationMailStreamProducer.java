package interview.guide.modules.invitation.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 面试邀约邮件任务生产者
 */
@Component
public class InvitationMailStreamProducer extends AbstractStreamProducer<Long> {

  public InvitationMailStreamProducer(RedisService redisService) {
    super(redisService);
  }

  @Override
  protected String taskDisplayName() {
    return "面试邀约邮件发送";
  }

  /** 对外暴露的入队入口（模板的 sendTask 为 protected） */
  public boolean send(Long invitationId) {
    return sendTask(invitationId);
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.INVITATION_MAIL_STREAM_KEY;
  }

  @Override
  protected Map<String, String> buildMessage(Long invitationId) {
    Map<String, String> message = new HashMap<>();
    message.put(AsyncTaskStreamConstants.FIELD_INVITATION_ID, String.valueOf(invitationId));
    message.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");
    return message;
  }

  @Override
  protected String payloadIdentifier(Long invitationId) {
    return "invitationId=" + invitationId;
  }

  @Override
  protected void onSendFailed(Long invitationId, String error) {
    // 入队失败由调用方根据返回值处理，此处仅记录
  }
}
