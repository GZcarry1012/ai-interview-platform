package interview.guide.common.security;

import io.jsonwebtoken.Claims;

/**
 * 认证用户加载器
 *
 * common 层定义契约、modules/auth 提供实现，
 * 避免 security 基础设施反向依赖业务模块
 */
public interface AuthUserLoader {

  /**
   * 按令牌声明加载当前用户
   *
   * @return 用户存在且状态正常时返回主体；用户不存在或被禁用时返回 null（请求按未授权处理）
   */
  UserPrincipal loadActiveUser(Claims claims);
}
