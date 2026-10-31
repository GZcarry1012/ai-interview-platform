package interview.guide.modules.auth.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

  ACTIVE("正常"),
  DISABLED("已禁用");

  private final String displayName;
}
