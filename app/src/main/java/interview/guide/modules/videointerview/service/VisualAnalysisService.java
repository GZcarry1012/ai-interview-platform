package interview.guide.modules.videointerview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.videointerview.config.MediaProcessProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * 视觉分析服务（qwen-vl 多模态）
 *
 * 对候选人面试视频关键帧做仪态/环境/在场性观察，
 * 输出结构化观察结果供评分 Agent 并入风险点
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualAnalysisService {

  private static final String ANALYSIS_PROMPT = """
      以下是候选人线上面试视频按时间顺序抽取的关键帧截图。请分析：
      1. 画面中是否始终有人且为同一人；
      2. 环境是否正式（室内、光线、背景整洁度）；
      3. 异常情况：多人出现、长时间无人、画面剧烈晃动、疑似他人协助、频繁离开画面。
      只输出一个 JSON 对象（不要 Markdown 代码块、不要解释）：
      {"personPresent": true, "environment": "一句话", "anomalies": ["异常1"], "observations": "一句话整体观察"}
      """;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LlmProviderRegistry llmProviderRegistry;
  private final MediaProcessProperties properties;

  public boolean isVlmConfigured() {
    String key = properties.getDashscopeApiKey();
    return key != null && !key.isBlank() && !key.startsWith("sk-placeholder");
  }

  /**
   * 分析关键帧（帧为 JPEG 字节），返回观察 JSON 字符串
   */
  public String analyze(List<byte[]> frames) {
    if (frames.isEmpty()) {
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "关键帧为空，无法分析");
    }
    List<byte[]> limited = frames.size() > properties.getMaxFramesPerAnalysis()
        ? downsample(frames, properties.getMaxFramesPerAnalysis())
        : frames;

    ChatClient chatClient = llmProviderRegistry.getPlainChatClient("dashscope-vlm");
    String content = chatClient.prompt()
        .user(u -> {
          u.text(ANALYSIS_PROMPT);
          for (byte[] frame : limited) {
            u.media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(frame));
          }
        })
        .call()
        .content();

    log.info("视觉分析完成: 帧数={}, 输出长度={}", limited.size(), content.length());
    return normalizeJson(content);
  }

  private List<byte[]> downsample(List<byte[]> frames, int max) {
    List<byte[]> result = new ArrayList<>();
    double step = (double) frames.size() / max;
    for (int i = 0; i < max; i++) {
      result.add(frames.get((int) Math.floor(i * step)));
    }
    return result;
  }

  /** 剥离可能的 Markdown 代码块包裹 */
  private String normalizeJson(String content) {
    if (content == null) {
      return "{}";
    }
    String trimmed = content.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
    }
    try {
      Map<String, Object> parsed = MAPPER.readValue(trimmed, Map.class);
      return MAPPER.writeValueAsString(parsed);
    } catch (Exception e) {
      log.warn("视觉分析输出非 JSON，原样保留");
      return trimmed;
    }
  }
}
