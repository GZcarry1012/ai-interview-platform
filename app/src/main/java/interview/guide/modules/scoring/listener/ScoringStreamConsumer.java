package interview.guide.modules.scoring.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.scoring.service.AgentScoringService;
import interview.guide.modules.videointerview.model.AiScoreStatus;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

/**
 * 候选人 AI 评分任务消费者
 *
 * 复用底座 Redis Stream 模板：失败自动重试 3 次；状态机 NOT_STARTED → RUNNING → COMPLETED / FAILED
 */
@Slf4j
@Component
public class ScoringStreamConsumer extends AbstractStreamConsumer<Long> {

  private final CandidateInterviewRepository interviewRepository;
  private final AgentScoringService agentScoringService;

  public ScoringStreamConsumer(RedisService redisService,
      CandidateInterviewRepository interviewRepository,
      AgentScoringService agentScoringService) {
    super(redisService);
    this.interviewRepository = interviewRepository;
    this.agentScoringService = agentScoringService;
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
  protected String groupName() {
    return AsyncTaskStreamConstants.SCORING_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.SCORING_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "candidate-scoring-consumer";
  }

  @Override
  protected Long parsePayload(StreamMessageId messageId, Map<String, String> data) {
    String id = data.get(AsyncTaskStreamConstants.FIELD_CANDIDATE_INTERVIEW_ID);
    return id == null || id.isBlank() ? null : Long.parseLong(id);
  }

  @Override
  protected String payloadIdentifier(Long interviewId) {
    return "interviewId=" + interviewId;
  }

  @Override
  protected void markProcessing(Long interviewId) {
    interviewRepository.findById(interviewId).ifPresent(interview -> {
      interview.setAiScoreStatus(AiScoreStatus.RUNNING);
      interview.setAiScoreError(null);
      interviewRepository.save(interview);
    });
  }

  @Override
  protected void processBusiness(Long interviewId) {
    agentScoringService.runScoring(interviewId);
  }

  @Override
  protected void markCompleted(Long interviewId) {
    interviewRepository.findById(interviewId).ifPresent(interview -> {
      interview.setAiScoreStatus(AiScoreStatus.COMPLETED);
      interviewRepository.save(interview);
    });
  }

  @Override
  protected void markFailed(Long interviewId, String error) {
    interviewRepository.findById(interviewId).ifPresent(interview -> {
      interview.setAiScoreStatus(AiScoreStatus.FAILED);
      interview.setAiScoreError(error);
      interviewRepository.save(interview);
    });
  }

  @Override
  protected void retryMessage(Long interviewId, int retryCount) {
    interviewRepository.findById(interviewId).ifPresent(interview -> {
      interview.setAiScoreStatus(AiScoreStatus.NOT_STARTED);
      interviewRepository.save(interview);
    });
    Map<String, String> message = new HashMap<>();
    message.put(AsyncTaskStreamConstants.FIELD_CANDIDATE_INTERVIEW_ID,
        String.valueOf(interviewId));
    message.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount));
    redisService().streamAdd(streamKey(), message, AsyncTaskStreamConstants.STREAM_MAX_LEN);
  }
}
