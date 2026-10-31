package interview.guide.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求
 *
 * 开放注册仅创建求职者（USER）角色，企业端角色由管理员在后台分配
 */
public record RegisterRequest(
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "用户名仅支持字母、数字、下划线和中划线，长度 3-32")
    String username,

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    String password,

    @NotBlank(message = "显示名不能为空")
    @Size(max = 64, message = "显示名最长 64 字符")
    String displayName,

    @Size(max = 128, message = "邮箱最长 128 字符")
    String email
) {
}
