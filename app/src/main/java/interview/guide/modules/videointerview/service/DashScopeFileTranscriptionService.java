package interview.guide.modules.videointerview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.modules.videointerview.config.MediaProcessProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * DashScope 文件转写服务（REST 直连，paraformer-v2）
 *
 * 流程：提交音频公网 URL 创建转写任务 → 轮询任务状态 →
 * SUCCEEDED 后下载转写结果 JSON 并拼接全文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeFileTranscriptionService {

  private static final String SUBMIT_URL =
      "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription";
  private static final Duration TASK_TIMEOUT = Duration.ofMinutes(5);

  private final MediaProcessProperties mediaProperties;
  private final RestClient restClient = RestClient.create();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public boolean isAsrConfigured() {
    String key = mediaProperties.getDashscopeApiKey();
    return key != null && !key.isBlank() && !key.startsWith("sk-placeholder");
  }

  /**
   * 转写音频（阻塞轮询，2 分钟音频约 30-90 秒）
   */
  public String transcribe(String audioPublicUrl) {
    String taskId = submitTask(audioPublicUrl);
    JsonNode task = pollTask(taskId);

    String status = task.path("task_status").asText("");
    if (!"SUCCEEDED".equals(status)) {
      String message = task.path("message").asText("转写任务未成功");
      throw new IllegalStateException("转写任务 " + status + ": " + message);
    }

    String transcriptionUrl = task.path("results").path(0)
        .path("transcription_url").asText(null);
    if (transcriptionUrl == null || transcriptionUrl.isBlank()) {
      return "";
    }
    return fetchTranscriptText(transcriptionUrl);
  }

  private String submitTask(String audioUrl) {
    var response = restClient.post()
        .uri(SUBMIT_URL)
        .header("Authorization", "Bearer " + mediaProperties.getDashscopeApiKey())
        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
        .body(Map.of("model", "paraformer-v2",
            "input", Map.of("file_urls", List.of(audioUrl)),
            "parameters", Map.of("language_hints", List.of("zh"))))
        .retrieve()
        .body(String.class);
    return parseJson(response).path("output").path("task_id").asText();
  }

  private JsonNode pollTask(String taskId) {
    long deadline = System.currentTimeMillis() + TASK_TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline) {
      sleep();
      var response = restClient.get()
          .uri(SUBMIT_URL + "?task_id=" + taskId)
          .header("Authorization", "Bearer " + mediaProperties.getDashscopeApiKey())
          .retrieve()
          .body(String.class);
      JsonNode output = parseJson(response).path("output");
      String status = output.path("task_status").asText("PENDING");
      log.info("转写任务轮询: taskId={}, status={}", taskId, status);
      if ("SUCCEEDED".equals(status) || "FAILED".equals(status)
          || "CANCELED".equals(status) || "UNKNOWN".equals(status)) {
        return output;
      }
    }
    throw new IllegalStateException("转写任务超时");
  }

  /** 结果文件为 JSON 数组，逐层收集全部 text 字段拼接 */
  private String fetchTranscriptText(String resultUrl) {
    var response = restClient.get().uri(resultUrl).retrieve().body(String.class);
    JsonNode root = parseJson(response);
    StringBuilder text = new StringBuilder();
    collectTexts(root, text);
    return text.toString().trim();
  }

  private void collectTexts(JsonNode node, StringBuilder text) {
    if (node.isTextual() && !node.asText().isBlank()) {
      text.append(node.asText());
      return;
    }
    if (node.isContainerNode()) {
      // 句子级 text 拼接后补空格，其余层级递归
      if (node.has("text") && node.path("text").isTextual()) {
        text.append(node.path("text").asText());
        return;
      }
      for (JsonNode child : node) {
        collectTexts(child, text);
      }
    }
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body == null ? "{}" : body);
    } catch (Exception e) {
      throw new IllegalStateException("转写响应解析失败: " + e.getMessage(), e);
    }
  }

  private void sleep() {
    try {
      Thread.sleep(4000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
