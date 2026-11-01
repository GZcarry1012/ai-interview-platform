package interview.guide.modules.scoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 评分进度 SSE 发射器
 *
 * 复核页通过 score-stream 端点注册连接，评分链路上的关键动作
 * （工具调用/综合评分/完成/失败）实时推送到前端时间线；
 * done/error 事件后自动 complete，注册表防泄漏
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringProgressEmitter {

  private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  public SseEmitter register(Long interviewId) {
    SseEmitter emitter = new SseEmitter(600_000L);
    CopyOnWriteArrayList<SseEmitter> list =
        emitters.computeIfAbsent(interviewId, key -> new CopyOnWriteArrayList<>());
    list.add(emitter);
    Runnable cleanup = () -> remove(interviewId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    return emitter;
  }

  public void emit(Long interviewId, String eventName, Map<String, Object> data) {
    List<SseEmitter> list = emitters.get(interviewId);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event()
            .name(eventName)
            .data(objectMapper.writeValueAsString(data), MediaType.APPLICATION_JSON));
        if ("done".equals(eventName) || "error".equals(eventName)) {
          emitter.complete();
        }
      } catch (Exception e) {
        log.debug("SSE 推送失败，移除连接: interviewId={}", interviewId);
        remove(interviewId, emitter);
      }
    }
  }

  private void remove(Long interviewId, SseEmitter emitter) {
    var list = emitters.get(interviewId);
    if (list != null) {
      list.remove(emitter);
      if (list.isEmpty()) {
        emitters.remove(interviewId);
      }
    }
  }
}
