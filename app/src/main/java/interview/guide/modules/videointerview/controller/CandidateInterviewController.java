package interview.guide.modules.videointerview.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.modules.videointerview.dto.CandidateSessionDTO;
import interview.guide.modules.videointerview.service.CandidateInterviewService;
import interview.guide.modules.videointerview.service.VideoUploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人视频面试接口（免登录，凭邀约 token）
 *
 * 上传协议：
 * 1. POST /{token}/upload/init          — 秒传校验 + 建会话，返回已上传分片（断点续传）
 * 2. POST /{token}/upload/{uploadId}/{index} — 单分片原始字节流（幂等，可并发）
 * 3. POST /{token}/upload/{uploadId}/complete — 合并 → 归档 RustFS → 登记哈希
 */
@Slf4j
@RestController
@RequestMapping("/api/candidate")
@RequiredArgsConstructor
public class CandidateInterviewController {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final CandidateInterviewService interviewService;
  private final VideoUploadService uploadService;

  @GetMapping("/{token}/session")
  public Result<CandidateSessionDTO> session(@PathVariable String token) {
    return Result.success(interviewService.getSession(token));
  }

  public record UploadInitRequest(
      @NotBlank(message = "文件名不能为空") String fileName,
      long fileSize,
      int totalChunks,
      int chunkSize,
      @NotBlank(message = "文件哈希不能为空") String fileHash
  ) {
  }

  public record UploadInitResponse(String uploadId, boolean instantUpload,
      List<Integer> uploadedChunks) {
  }

  @PostMapping("/{token}/upload/init")
  public Result<UploadInitResponse> initUpload(
      @PathVariable String token,
      @Valid @RequestBody UploadInitRequest request) {
    VideoUploadService.InitResult result = uploadService.init(token, request.fileName(),
        request.fileSize(), request.totalChunks(), request.chunkSize(), request.fileHash());
    return Result.success(new UploadInitResponse(result.uploadId(), result.instantUpload(),
        result.uploadedChunks()));
  }

  @PostMapping(value = "/{token}/upload/{uploadId}/{index}",
      consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  public Result<Void> uploadChunk(
      @PathVariable String token,
      @PathVariable String uploadId,
      @PathVariable int index,
      HttpServletRequest request) {
    try (var in = request.getInputStream()) {
      uploadService.saveChunk(token, uploadId, index, in);
    } catch (IOException e) {
      log.warn("分片读取失败: uploadId={}, index={}", uploadId, index, e);
      throw new BusinessException(ErrorCode.VIDEO_CHUNK_INVALID,
          "分片读取失败，请重试");
    }
    return Result.success();
  }

  public record UploadCompleteRequest(Integer durationSeconds) {
  }

  @PostMapping("/{token}/upload/{uploadId}/complete")
  public Result<String> completeUpload(
      @PathVariable String token,
      @PathVariable String uploadId,
      @RequestBody(required = false) UploadCompleteRequest request) {
    Integer duration = request != null ? request.durationSeconds() : null;
    return Result.success(uploadService.complete(token, uploadId, duration));
  }

  public record AnswerItem(Integer index, String question, String answer) {
  }

  public record AnswersRequest(List<AnswerItem> answers) {
  }

  @PostMapping("/{token}/answers")
  public Result<CandidateSessionDTO> submitAnswers(
      @PathVariable String token,
      @RequestBody AnswersRequest request) {
    try {
      String answersJson = MAPPER.writeValueAsString(
          request.answers() != null ? request.answers() : List.of());
      return Result.success(interviewService.submitAnswers(token, answersJson));
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.BAD_REQUEST,
          "答卷序列化失败");
    }
  }
}
