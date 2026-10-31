package interview.guide.modules.videointerview.service;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.scoring.model.AgentScoreReport;
import interview.guide.modules.videointerview.dto.ReviewDetailDTO;
import interview.guide.modules.videointerview.model.AiScoreStatus;
import interview.guide.modules.videointerview.model.CandidateInterviewStatus;
import interview.guide.modules.videointerview.model.HumanDecision;
import java.io.ByteArrayOutputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 候选人面试归档报告 PDF
 *
 * 满足审计留档要求：面试元信息 + AI 评分报告 + 答卷全文 + 人工复核结论
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateReportPdfService {

  private final ReviewService reviewService;

  public byte[] exportInterviewReport(Long interviewId) {
    ReviewDetailDTO detail = reviewService.detail(interviewId);
    try (var out = new ByteArrayOutputStream()) {
      PdfWriter writer = new PdfWriter(out);
      PdfDocument pdf = new PdfDocument(writer);
      Document document = new Document(pdf);
      document.setFont(createCjkFont());

      document.add(new Paragraph("候选人视频面试归档报告")
          .setFontSize(18));
      document.add(new Paragraph("面试会话 #" + interviewId
          + " · 生成时间 " + java.time.LocalDateTime.now()
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).setFontSize(9));

      document.add(new Paragraph("一、基本信息").setFontSize(13).setMarginTop(16));
      document.add(metaTable(detail));

      document.add(new Paragraph("二、AI 评分报告").setFontSize(13).setMarginTop(16));
      document.add(scoreSection(detail));

      document.add(new Paragraph("三、答卷全文").setFontSize(13).setMarginTop(16));
      for (ReviewDetailDTO.AnswerItem answer : detail.answers()) {
        document.add(new Paragraph((answer.index() + 1) + ". " + answer.question())
            .setMarginTop(8));
        document.add(new Paragraph(safe(answer.answer())).setFontSize(10));
      }

      document.add(new Paragraph("四、人工复核结论").setFontSize(13).setMarginTop(16));
      document.add(new Paragraph(
          detail.humanDecision().getDisplayName()
              + (detail.humanComment() != null && !detail.humanComment().isBlank()
              ? "：" + detail.humanComment() : "")
              + (detail.humanReviewedByName() != null
              ? "（复核人：" + detail.humanReviewedByName() + "）" : "")
      ).setFontSize(10));

      document.close();
      return out.toByteArray();
    } catch (Exception e) {
      log.error("生成面试归档报告失败: interviewId={}", interviewId, e);
      throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED);
    }
  }

  private Table metaTable(ReviewDetailDTO detail) {
    Table table = new Table(UnitValue.createPercentArray(new float[]{25, 75}))
        .useAllAvailableWidth();
    table.addCell(cell("候选人", true));
    table.addCell(cell(detail.engineerName(), false));
    table.addCell(cell("应聘岗位", true));
    table.addCell(cell(safe(detail.position()), false));
    table.addCell(cell("面试时长", true));
    table.addCell(cell(detail.durationSeconds() != null ? detail.durationSeconds() + " 秒" : "—", false));
    table.addCell(cell("答卷提交", true));
    table.addCell(cell(detail.submittedAt() != null
        ? detail.submittedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        : "—", false));
    return table;
  }

  private Table scoreSection(ReviewDetailDTO detail) {
    Table table = new Table(UnitValue.createPercentArray(new float[]{25, 75}))
        .useAllAvailableWidth();
    if (detail.aiScoreStatus() != AiScoreStatus.COMPLETED || detail.aiScore() == null) {
      table.addCell(cell("状态", true));
      table.addCell(cell("AI 评分未完成（" + detail.aiScoreStatus().getDisplayName()
          + (detail.aiScoreError() != null ? "：" + detail.aiScoreError() : "") + "）", false));
      return table;
    }
    AgentScoreReport report = detail.aiScore();
    table.addCell(cell("总分", true));
    table.addCell(cell(report.overallScore() + " / 100", false));
    table.addCell(cell("建议结论", true));
    table.addCell(cell(report.recommendation() + "（" + safe(report.recommendationReason()) + "）", false));
    for (AgentScoreReport.DimensionScore dimension : report.dimensions()) {
      table.addCell(cell(dimension.name(), true));
      table.addCell(cell(dimension.score() + " 分 · " + safe(dimension.comment()), false));
    }
    if (report.risks() != null && !report.risks().isEmpty()) {
      table.addCell(cell("风险点", true));
      table.addCell(cell(String.join("；", report.risks()), false));
    }
    table.addCell(cell("评语摘要", true));
    table.addCell(cell(safe(report.summary()), false));
    return table;
  }

  private Cell cell(String text, boolean header) {
    return new Cell().add(new Paragraph(text)
        .setFontSize(9));
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "—" : value;
  }

  private PdfFont createCjkFont() {
    try {
      return PdfFontFactory.createFont("fonts/ZhuqueFangsong-Regular.ttf",
          com.itextpdf.io.font.PdfEncodings.IDENTITY_H);
    } catch (Exception e) {
      log.warn("CJK 字体加载失败，回退默认字体: {}", e.getMessage());
      return null;
    }
  }
}
