package interview.guide.modules.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织实体
 *
 * 支撑平台方 / 合作伙伴双层组织架构，为后续数据隔离与权限边界预留
 */
@Getter
@Setter
@Entity
@Table(name = "organizations")
public class OrganizationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "org_type", nullable = false, length = 32)
  private OrganizationType orgType;

  @Column(name = "contact_email", length = 128)
  private String contactEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private UserStatus status = UserStatus.ACTIVE;

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
