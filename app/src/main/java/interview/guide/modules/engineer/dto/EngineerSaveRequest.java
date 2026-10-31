package interview.guide.modules.engineer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 / 重新提交工程师资料请求（multipart 的 info part）
 */
public record EngineerSaveRequest(
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名最长 64 字符")
    String name,

    @Size(max = 32, message = "手机号最长 32 字符")
    String phone,

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    String email,

    @NotBlank(message = "应聘岗位不能为空")
    @Size(max = 64, message = "岗位最长 64 字符")
    String position,

    Integer experienceYears,

    @Size(max = 32, message = "学历最长 32 字符")
    String education,

    @Size(max = 2000, message = "技能清单最长 2000 字符")
    String skills
) {
}
