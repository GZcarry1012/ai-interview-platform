package interview.guide.common.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security 用户主体
 *
 * 携带数据库主键、角色与组织 ID（组织用于合作伙伴数据隔离），供鉴权与审计使用
 */
public record UserPrincipal(Long id, String username, String role, Long orgId, boolean active)
    implements UserDetails {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Override
  public String getPassword() {
    // 认证由 JWT 完成，过滤器侧不再校验密码
    return "";
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return active;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }
}
