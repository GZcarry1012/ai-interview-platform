package interview.guide.modules.invitation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送面试邀约请求
 */
public record InvitationSendRequest(
    @NotNull(message = "预约面试时间不能为空")
    @Future(message = "预约时间必须晚于当前时间")
    LocalDateTime scheduledTime,

    @Size(max = 128, message = "岗位说明最长 128 字符")
    String position,

    /** 自定义面试问题（可空，空则用平台默认题） */
    List<@Size(max = 500, message = "单个问题最长 500 字符") String> questions
) {
}
