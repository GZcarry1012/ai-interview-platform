package interview.guide.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 认证配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth.jwt")
public class JwtProperties {

  /** 签名密钥（HS256，至少 32 字节），从 APP_AUTH_JWT_SECRET 注入 */
  private String secret;

  /** 访问令牌有效期（分钟） */
  private int accessTokenExpireMinutes = 120;

  /** 刷新令牌有效期（天） */
  private int refreshTokenExpireDays = 7;
}
