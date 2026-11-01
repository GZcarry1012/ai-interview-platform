package interview.guide.modules.videointerview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 媒体处理配置（FFmpeg + 多模态评分）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.media")
public class MediaProcessProperties {

  /** ffmpeg 可执行文件路径；空则使用 PATH 中的 ffmpeg */
  private String ffmpegPath = "";

  /** 视觉分析抽帧间隔（秒） */
  private int keyframeIntervalSeconds = 10;

  /** 视觉分析 VLM 模型（DashScope 兼容模式） */
  private String vlmModel = "qwen-vl-plus";

  /** DashScope API Key（与语音服务共用 AI_BAILIAN_API_KEY，见 application.yml） */
  private String dashscopeApiKey = "";

  /** 单次 VLM 分析的最大帧数（控制 token 成本） */
  private int maxFramesPerAnalysis = 12;
}
