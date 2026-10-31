package interview.guide.modules.scoring.listener;

import interview.guide.common.async.AbstractStreamProducer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 候选人 AI 评分任务生产者
 */
@Component
public class ScoringStreamProducer extends AbstractStreamProducer<Long> {

  public ScoringStreamProducer(RedisService redisService) {
    super(redisService);
  }

  @Override
  protected String taskDisplayName() {
    return "候选人 AI 评分";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.SCORING_STREAM_KEY;
  }

  @Override
  protected Map<String, String> buildMessage(Long interviewId) {
    Map<String, String> message = new HashMap<>();
    message.put(AsyncTaskStreamConstants.FIELD_CANDIDATE_INTERVIEW_ID,
        String.valueOf(interviewId));
    message.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0");
    return message;
  }

  @Override
  protected String payloadIdentifier(Long interviewId) {
    return "interviewId=" + interviewId;
  }

  @Override
  protected void onSendFailed(Long interviewId, String error) {
    // 入队失败由调用方提示用户重试
  }

  /** 对外入队入口（模板方法为 protected） */
  public boolean enqueue(Long interviewId) {
    return sendTask(interviewId);
  }
}
