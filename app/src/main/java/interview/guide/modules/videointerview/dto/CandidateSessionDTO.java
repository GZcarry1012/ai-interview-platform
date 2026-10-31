package interview.guide.modules.videointerview.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 候选人面试会话信息（公开接口，最小字段集）
 *
 * 身份信息（姓名/岗位/时间）由 /api/invitations/token/** 提供，此接口专注面试状态
 */
public record CandidateSessionDTO(
    String status,
    String statusDisplayName,
    List<String> questions,
    boolean videoUploaded,
    boolean answersSubmitted,
    Integer durationSeconds,
    LocalDateTime submittedAt
) {
}
