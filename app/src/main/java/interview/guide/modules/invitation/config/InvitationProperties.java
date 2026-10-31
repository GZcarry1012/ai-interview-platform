package interview.guide.modules.invitation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 面试邀约配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.invitation")
public class InvitationProperties {

  /** 前端基地址，用于拼接候选人邀约确认链接 */
  private String frontendBaseUrl = "http://localhost:5173";

  /** 邮件发件人显示名 */
  private String mailFromName = "AI 面试平台";
}
