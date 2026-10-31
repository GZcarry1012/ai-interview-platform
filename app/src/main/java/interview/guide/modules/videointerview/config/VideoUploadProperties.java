package interview.guide.modules.videointerview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视频上传配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.video-upload")
public class VideoUploadProperties {

  /** 分片暂存目录（合并前落盘，重启可续传；空则用系统临时目录） */
  private String stagingDir = "";

  /** 视频大小上限（MB） */
  private int maxVideoSizeMb = 500;

  /** 平台默认面试问题（邀约未自定义时使用） */
  private java.util.List<String> defaultQuestions = java.util.List.of(
      "请先用 2 分钟做一段自我介绍，并说明与目标岗位的匹配点。",
      "请介绍一个你最有代表性的项目：背景、你的职责、遇到的最大挑战和结果。",
      "结合你的经验，谈谈如何保证交付代码的质量与稳定性。"
  );
}
