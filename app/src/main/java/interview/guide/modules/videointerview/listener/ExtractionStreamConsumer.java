package interview.guide.modules.videointerview.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.infrastructure.mediaprocess.FfmpegMediaService;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.TranscriptStatus;
import interview.guide.modules.videointerview.model.VisualStatus;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import interview.guide.modules.videointerview.service.DashScopeFileTranscriptionService;
import interview.guide.modules.videointerview.service.VisualAnalysisService;
import interview.guide.modules.videointerview.config.MediaProcessProperties;
import java.nio.file.Path;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

/**
 * 多模态抽取任务消费者
 *
 * 单任务串联两条支线：
 * 1. FFmpeg 抽音轨 → RustFS 公开读 → DashScope 文件转写 → transcript
 * 2. FFmpeg 抽关键帧 → qwen-vl 观察 → visual_analysis
 * 任一支线前置不满足（无 Key/无 ffmpeg）时对应状态置 SKIPPED，不阻塞另一支线
 */
@Slf4j
@Component
public class ExtractionStreamConsumer extends AbstractStreamConsumer<Long> {

  private final CandidateInterviewRepository interviewRepository;
  private final FileStorageService fileStorageService;
  private final FfmpegMediaService ffmpegMediaService;
  private final DashScopeFileTranscriptionService transcriptionService;
  private final VisualAnalysisService visualAnalysisService;

  public ExtractionStreamConsumer(RedisService redisService,
      CandidateInterviewRepository interviewRepository,
      FileStorageService fileStorageService,
      FfmpegMediaService ffmpegMediaService,
      DashScopeFileTranscriptionService transcriptionService,
      VisualAnalysisService visualAnalysisService) {
    super(redisService);
    this.interviewRepository = interviewRepository;
    this.fileStorageService = fileStorageService;
    this.ffmpegMediaService = ffmpegMediaService;
    this.transcriptionService = transcriptionService;
    this.visualAnalysisService = visualAnalysisService;
  }

  @Override
  protected String taskDisplayName() {
    return "视频多模态抽取";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.VIDEO_EXTRACT_STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return AsyncTaskStreamConstants.VIDEO_EXTRACT_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.VIDEO_EXTRACT_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "video-extract-consumer";
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
      interview.setTranscriptStatus(interview.getVideoFileKey() != null
          ? TranscriptStatus.EXTRACTING : TranscriptStatus.SKIPPED);
      interview.setVisualStatus(interview.getVideoFileKey() != null
          ? VisualStatus.ANALYZING : VisualStatus.SKIPPED);
      interview.setTranscriptError(null);
      interview.setVisualError(null);
      interviewRepository.save(interview);
    });
  }

  @Override
  protected void processBusiness(Long interviewId) {
    CandidateInterviewEntity interview = interviewRepository.findById(interviewId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
    if (interview.getVideoFileKey() == null || interview.getVideoFileKey().isBlank()) {
      markSkipped(interview, "无已归档视频");
      return;
    }

    File videoFile = null;
    try (InputStream videoStream = fileStorageService.openStream(interview.getVideoFileKey())) {
      videoFile = ffmpegMediaService.downloadToTempFile(videoStream);

      // 支线一：音轨 → 转写
      if (transcriptionService.isAsrConfigured()) {
        try {
          interview.setTranscriptStatus(TranscriptStatus.EXTRACTING);
          interviewRepository.save(interview);
          File audio = ffmpegMediaService.extractAudio(videoFile.toPath());
          try {
            String audioKey = fileStorageService.uploadLocalFilePublic(audio,
                "interview-audio", "interview-" + interviewId + ".mp3",
                "audio/mpeg");
            String audioUrl = fileStorageService.getFileUrl(audioKey);
            interview.setTranscriptStatus(TranscriptStatus.TRANSCRIBING);
            interviewRepository.save(interview);
            String transcript = transcriptionService.transcribe(audioUrl);
            interview.setTranscript(transcript);
            interview.setTranscriptStatus(TranscriptStatus.COMPLETED);
            interviewRepository.save(interview);
          } finally {
            if (!audio.delete()) {
              log.warn("临时音频清理失败: {}", audio.getAbsolutePath());
            }
          }
        } catch (Exception e) {
          interview.setTranscriptStatus(TranscriptStatus.FAILED);
          interview.setTranscriptError(String.valueOf(e.getMessage()));
          interviewRepository.save(interview);
          log.error("语音转写支线失败: interviewId={}", interviewId, e);
        }
      } else {
        interview.setTranscriptStatus(TranscriptStatus.SKIPPED);
        interview.setTranscriptError("未配置 DashScope Key，转写跳过");
        interviewRepository.save(interview);
      }

      // 支线二：关键帧 → 视觉分析
      if (visualAnalysisService.isVlmConfigured()) {
        try {
          List<File> frames = ffmpegMediaService.extractKeyframes(
              videoFile.toPath(), 10);
          try {
            interview.setVisualStatus(VisualStatus.ANALYZING);
            interviewRepository.save(interview);
            List<byte[]> frameBytes = frames.stream()
                .map(this::readBytes)
                .collect(java.util.stream.Collectors.toList());
            String analysis = visualAnalysisService.analyze(frameBytes);
            interview.setVisualAnalysis(analysis);
            interview.setVisualStatus(VisualStatus.COMPLETED);
            interviewRepository.save(interview);
          } finally {
            for (File frame : frames) {
              if (!frame.delete()) {
                log.warn("临时帧清理失败: {}", frame.getAbsolutePath());
              }
            }
            deleteFrameDir(videoFile.toPath().resolveSibling(
                "frames-" + videoFile.toPath().getFileName()));
          }
        } catch (Exception e) {
          interview.setVisualStatus(VisualStatus.FAILED);
          interview.setVisualError(String.valueOf(e.getMessage()));
          interviewRepository.save(interview);
          log.error("视觉分析支线失败: interviewId={}", interviewId, e);
        }
      } else {
        interview.setVisualStatus(VisualStatus.SKIPPED);
        interview.setVisualError("未配置 DashScope Key，视觉分析跳过");
        interviewRepository.save(interview);
      }
    } catch (Exception e) {
      // 下载/FFmpeg 整体失败：两条支线同时标记
      interviewRepository.findById(interviewId).ifPresent(entity -> {
        entity.setTranscriptStatus(TranscriptStatus.FAILED);
        entity.setTranscriptError("视频下载或处理失败: " + e.getMessage());
        entity.setVisualStatus(VisualStatus.FAILED);
        entity.setVisualError("视频下载或处理失败: " + e.getMessage());
        interviewRepository.save(entity);
      });
      log.error("多模态抽取失败: interviewId={}", interviewId, e);
    } finally {
      if (videoFile != null && !videoFile.delete()) {
        log.warn("临时视频清理失败: {}", videoFile.getAbsolutePath());
      }
    }
  }

  @Override
  protected void markCompleted(Long interviewId) {
    // 支线状态在 processBusiness 内已落库，这里仅记日志
    log.info("多模态抽取任务完成: interviewId={}", interviewId);
  }

  @Override
  protected void markFailed(Long interviewId, String error) {
    interviewRepository.findById(interviewId).ifPresent(interview -> {
      interview.setTranscriptStatus(TranscriptStatus.FAILED);
      interview.setTranscriptError(error);
      interview.setVisualStatus(VisualStatus.FAILED);
      interview.setVisualError(error);
      interviewRepository.save(interview);
    });
  }

  @Override
  protected void retryMessage(Long interviewId, int retryCount) {
    Map<String, String> message = new HashMap<>();
    message.put(AsyncTaskStreamConstants.FIELD_CANDIDATE_INTERVIEW_ID,
        String.valueOf(interviewId));
    message.put(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount));
    redisService().streamAdd(streamKey(), message, AsyncTaskStreamConstants.STREAM_MAX_LEN);
  }

  private void markSkipped(CandidateInterviewEntity interview, String reason) {
    interview.setTranscriptStatus(TranscriptStatus.SKIPPED);
    interview.setTranscriptError(reason);
    interview.setVisualStatus(VisualStatus.SKIPPED);
    interview.setVisualError(reason);
    interviewRepository.save(interview);
  }

  private byte[] readBytes(File file) {
    try {
      return java.nio.file.Files.readAllBytes(file.toPath());
    } catch (java.io.IOException e) {
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED, "关键帧读取失败");
    }
  }

  private void deleteFrameDir(Path dir) {
    File[] files = dir.toFile().listFiles();
    if (files != null) {
      for (File f : files) {
        if (!f.delete()) {
          log.warn("帧目录清理失败: {}", f.getAbsolutePath());
        }
      }
    }
    if (!dir.toFile().delete()) {
      log.warn("帧目录删除失败: {}", dir);
    }
  }
}
