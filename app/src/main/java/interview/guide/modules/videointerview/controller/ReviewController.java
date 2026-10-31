package interview.guide.modules.videointerview.controller;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.Result;
import interview.guide.common.security.UserPrincipal;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.videointerview.dto.ReviewDetailDTO;
import interview.guide.modules.videointerview.dto.ReviewListItemDTO;
import interview.guide.modules.videointerview.model.HumanDecision;
import interview.guide.modules.videointerview.service.CandidateReportPdfService;
import interview.guide.modules.videointerview.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人工复核工作台接口（REVIEWER / ADMIN）
 */
@RestController
@RequestMapping("/api/review/interviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
public class ReviewController {

  private final ReviewService reviewService;
  private final FileStorageService fileStorageService;
  private final CandidateReportPdfService pdfService;

  @GetMapping
  public Result<List<ReviewListItemDTO>> list() {
    return Result.success(reviewService.listSubmitted());
  }

  @GetMapping("/{id}")
  public Result<ReviewDetailDTO> detail(@PathVariable Long id) {
    return Result.success(reviewService.detail(id));
  }

  /**
   * 触发 AI 评分（异步执行，前端轮询状态）
   */
  @PostMapping("/{id}/ai-score")
  public Result<Void> triggerAiScore(
      @PathVariable Long id,
      @AuthenticationPrincipal UserPrincipal operator) {
    reviewService.triggerAiScore(id, operator);
    return Result.success();
  }

  public record HumanReviewRequest(
      @NotBlank(message = "复核结论不能为空") String decision,
      String comment
  ) {
  }

  @PostMapping("/{id}/human-review")
  public Result<ReviewDetailDTO> humanReview(
      @PathVariable Long id,
      @Valid @RequestBody HumanReviewRequest request,
      @AuthenticationPrincipal UserPrincipal operator) {
    HumanDecision decision = switch (request.decision().toUpperCase()) {
      case "APPROVED" -> HumanDecision.APPROVED;
      case "REJECTED" -> HumanDecision.REJECTED;
      default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
          "复核结论仅支持 APPROVED/REJECTED");
    };
    return Result.success(reviewService.humanReview(id, decision, request.comment(), operator));
  }

  /**
   * 面试视频流式播放（大文件流式读取，不整载内存）
   */
  @GetMapping("/{id}/video")
  public ResponseEntity<InputStreamResource> video(@PathVariable Long id) {
    String fileKey = reviewService.videoFileKey(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "该面试尚未归档视频"));
    long size = reviewService.videoFileSize(id);
    var resource = new InputStreamResource(fileStorageService.openStream(fileKey));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"interview.webm\"")
        .contentType(MediaType.parseMediaType("video/webm"))
        .body(resource);
  }

  /**
   * PDF 归档报告（审计留档）
   */
  @GetMapping("/{id}/report.pdf")
  public ResponseEntity<byte[]> reportPdf(@PathVariable Long id) {
    byte[] pdf = pdfService.exportInterviewReport(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"interview-report-" + id + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }
}
