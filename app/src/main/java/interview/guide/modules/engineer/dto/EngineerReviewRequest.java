package interview.guide.modules.engineer.dto;

import jakarta.validation.constraints.Size;

/**
 * 审核操作请求（通过 / 驳回）
 */
public record EngineerReviewRequest(
    @Size(max = 1000, message = "审核意见最长 1000 字符")
    String comment
) {
}
