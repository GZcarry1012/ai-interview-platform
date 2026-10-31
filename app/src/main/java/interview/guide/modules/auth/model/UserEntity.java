package interview.guide.modules.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户实体
 *
 * 覆盖求职者（C 端）与企业端角色（业务负责人/审核人/面试官/管理员），
 * 密码仅存 BCrypt 哈希
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "username", nullable = false, unique = true, length = 64)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 64)
  private String displayName;

  @Column(name = "email", length = 128)
  private String email;

  @Column(name = "phone", length = 32)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "org_type", length = 32)
  private OrganizationType orgType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_id")
  private OrganizationEntity organization;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @jakarta.persistence.PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @jakarta.persistence.PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
