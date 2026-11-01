package interview.guide.infrastructure.mediaprocess;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.videointerview.config.MediaProcessProperties;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FFmpeg 媒体处理服务
 *
 * 视频抽取音轨（供 ASR 文件转写）与关键帧（供 VLM 视觉分析）；
 * 输入输出均走临时文件，调用方负责删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegMediaService {

  private static final long PROCESS_TIMEOUT_SECONDS = 300;

  private final MediaProcessProperties properties;

  /**
   * 抽取音轨为 16k 单声道 mp3（ASR 友好格式）
   *
   * @return 音频临时文件，调用方负责删除
   */
  public File extractAudio(Path videoFile) {
    File audio = tempFile("audio-", ".mp3");
    runFfmpeg("-y", "-i", videoFile.toAbsolutePath().toString(),
        "-vn", "-acodec", "libmp3lame", "-ar", "16000", "-ac", "1",
        audio.getAbsolutePath());
    return audio;
  }

  /**
   * 按间隔抽取关键帧（缩小至 640 宽控制 VLM token 成本）
   *
   * @return 帧图片临时文件列表（按帧序），调用方负责删除
   */
  public List<File> extractKeyframes(Path videoFile, int intervalSeconds) {
    Path frameDir = videoFile.resolveSibling("frames-" + videoFile.getFileName());
    File framePattern = frameDir.resolve("frame_%03d.jpg").toFile();
    runFfmpeg("-y", "-i", videoFile.toAbsolutePath().toString(),
        "-vf", "fps=1/" + Math.max(1, intervalSeconds) + ",scale=640:-2",
        "-q:v", "5", framePattern.getAbsolutePath());

    File[] frames = frameDir.toFile()
        .listFiles((dir, name) -> name.endsWith(".jpg"));
    if (frames == null || frames.length == 0) {
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "关键帧抽取结果为空");
    }
    java.util.Arrays.sort(frames);
    return List.of(frames);
  }

  /**
   * 将远端对象下载为本地临时视频文件（ffmpeg 需要可随机读取的输入）
   *
   * @return 视频临时文件，调用方负责删除
   */
  public File downloadToTempFile(InputStream videoStream) {
    try {
      File video = tempFile("video-", ".bin");
      Files.copy(videoStream, video.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return video;
    } catch (IOException e) {
      log.error("视频临时文件写入失败", e);
      throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "视频临时文件写入失败");
    }
  }

  private void runFfmpeg(String... args) {
    List<String> command = new ArrayList<>();
    command.add(resolveFfmpegBinary());
    command.addAll(List.of(args));

    log.info("执行 FFmpeg: {}", String.join(" ", shorten(command)));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    try {
      Process process = pb.start();
      String output = new String(process.getInputStream().readAllBytes(),
          java.nio.charset.StandardCharsets.UTF_8);
      boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "FFmpeg 处理超时");
      }
      if (process.exitValue() != 0) {
        log.error("FFmpeg 退出码 {}: {}", process.exitValue(),
            output.substring(0, Math.min(output.length(), 800)));
        throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "FFmpeg 处理失败");
      }
    } catch (BusinessException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("FFmpeg 执行失败", e);
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "FFmpeg 执行失败: " + e.getMessage());
    }
  }

  private String resolveFfmpegBinary() {
    String configured = properties.getFfmpegPath();
    return configured == null || configured.isBlank() ? "ffmpeg" : configured;
  }

  private List<String> shorten(List<String> command) {
    // 日志里截断超长参数，避免刷屏
    return command.stream()
        .map(a -> a.length() > 80 ? a.substring(0, 77) + "..." : a)
        .toList();
  }

  private File tempFile(String prefix, String suffix) {
    try {
      return File.createTempFile(prefix, suffix);
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "临时文件创建失败");
    }
  }
}
