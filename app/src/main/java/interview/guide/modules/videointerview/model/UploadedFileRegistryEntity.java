package interview.guide.modules.videointerview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 已归档文件登记表
 *
 * SHA-256 → 对象存储键的映射，支撑秒传与重复文件去重
 */
@Getter
@Setter
@Entity
@Table(name = "uploaded_file_registry")
public class UploadedFileRegistryEntity {

  @Id
  @Column(name = "file_hash", length = 64)
  private String fileHash;

  @Column(name = "file_key", nullable = false, length = 256)
  private String fileKey;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @jakarta.persistence.PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
