package interview.guide.modules.videointerview.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 语音转写状态
 *
 * SKIPPED 表示因未配置 ASR Key / FFmpeg 不可用等原因主动跳过（非失败），
 * 评 Agent 工具据此返回"无语音转写"
 */
@Getter
@AllArgsConstructor
public enum TranscriptStatus {

  NOT_STARTED("未转写"),
  EXTRACTING("抽取音轨"),
  TRANSCRIBING("转写中"),
  COMPLETED("转写完成"),
  SKIPPED("已跳过"),
  FAILED("转写失败");

  private final String displayName;
}


