package interview.guide.modules.videointerview.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 切片上传会话（持久化）
 *
 * 服务重启后凭 staging 目录中的分片文件 + 本记录继续断点续传
 */
@Getter
@Setter
@Entity
@Table(name = "video_upload_sessions")
public class VideoUploadSessionEntity {

  @Id
  @Column(name = "id", length = 64)
  private String id;

  @Column(name = "token", nullable = false, length = 64)
  private String token;

  @ManyToOne
  @JoinColumn(name = "interview_id", nullable = false)
  private CandidateInterviewEntity interview;

  @Column(name = "file_name", nullable = false, length = 256)
  private String fileName;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  /** 整文件 SHA-256，秒传校验键 */
  @Column(name = "file_hash", nullable = false, length = 64)
  private String fileHash;

  @Column(name = "total_chunks", nullable = false)
  private Integer totalChunks;

  @Column(name = "chunk_size", nullable = false)
  private Integer chunkSize;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @jakarta.persistence.PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }
}
