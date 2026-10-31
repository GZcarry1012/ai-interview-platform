package interview.guide.modules.auth.dto;

import interview.guide.modules.auth.model.UserEntity;

/**
 * 登录用户信息（不含密码哈希等敏感字段）
 */
public record UserDTO(
    Long id,
    String username,
    String displayName,
    String email,
    String role,
    String roleDisplayName,
    String orgType,
    String orgName,
    String status,
    String createdAt
) {

  public static UserDTO from(UserEntity user) {
    return new UserDTO(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getEmail(),
        user.getRole() != null ? user.getRole().name() : null,
        user.getRole() != null ? user.getRole().getDisplayName() : null,
        user.getOrgType() != null ? user.getOrgType().name() : null,
        user.getOrganization() != null ? user.getOrganization().getName() : null,
        user.getStatus() != null ? user.getStatus().name() : null,
        user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
    );
  }
}
