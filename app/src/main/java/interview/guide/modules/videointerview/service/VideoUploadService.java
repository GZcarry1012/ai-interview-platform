package interview.guide.modules.videointerview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.infrastructure.file.FileStorageService;
import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import interview.guide.modules.invitation.repository.InterviewInvitationRepository;
import interview.guide.modules.videointerview.config.VideoUploadProperties;
import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import interview.guide.modules.videointerview.model.CandidateInterviewStatus;
import interview.guide.modules.videointerview.model.UploadedFileRegistryEntity;
import interview.guide.modules.videointerview.model.VideoUploadSessionEntity;
import interview.guide.modules.videointerview.repository.CandidateInterviewRepository;
import interview.guide.modules.videointerview.repository.UploadedFileRegistryRepository;
import interview.guide.modules.videointerview.repository.VideoUploadSessionRepository;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频切片上传服务
 *
 * 流程：init（秒传校验/建会话）→ chunk（幂等落盘）→ complete（校验分片完整性 →
 * 合并 → 归档对象存储 → 登记哈希）。
 * 断点续传：分片落本地 staging 目录且会话持久化，服务重启后按目录内已有分片续传；
 * 秒传：客户端预计算整文件 SHA-256，登记表命中则跳过全部传输
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadService {

  private static final String VIDEO_PREFIX = "video-interviews";

  private final VideoUploadSessionRepository sessionRepository;
  private final CandidateInterviewRepository interviewRepository;
  private final UploadedFileRegistryRepository registryRepository;
  private final InterviewInvitationRepository invitationRepository;
  private final FileStorageService fileStorageService;
  private final VideoUploadProperties properties;
  private final TransactionalExecutor transactionalExecutor;

  /**
   * 初始化上传：命中秒传直接返回已归档 fileKey；否则建会话并返回已上传分片索引
   */
  @Transactional
  public InitResult init(String token, String fileName, long fileSize, int totalChunks,
      int chunkSize, String fileHash) {
    CandidateInterviewEntity interview = loadInterviewByToken(token);
    if (fileSize > (long) properties.getMaxVideoSizeMb() * 1024 * 1024) {
      throw new BusinessException(ErrorCode.VIDEO_SIZE_EXCEEDED,
          "视频大小超过 " + properties.getMaxVideoSizeMb() + "MB 限制");
    }

    // 秒传：同哈希文件已归档过，直接复用对象存储键
    var registered = registryRepository.findById(fileHash);
    if (registered.isPresent()) {
      attachVideo(interview, registered.get().getFileKey(), fileName, fileSize, fileHash);
      log.info("视频秒传命中: interviewId={}, hash={}", interview.getId(), fileHash);
      return new InitResult(null, true, List.of());
    }

    // 复用同 token + 同哈希的未完成会话（断点续传）
    VideoUploadSessionEntity session = sessionRepository
        .findByTokenAndFileHash(token, fileHash)
        .orElseGet(() -> {
          VideoUploadSessionEntity created = new VideoUploadSessionEntity();
          created.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
          created.setToken(token);
          created.setInterview(interview);
          created.setFileName(fileName);
          created.setFileSize(fileSize);
          created.setFileHash(fileHash);
          created.setTotalChunks(totalChunks);
          created.setChunkSize(chunkSize);
          return sessionRepository.save(created);
        });

    List<Integer> uploadedChunks = listUploadedChunks(session.getId());
    log.info("视频上传会话就绪: uploadId={}, 已有分片={}", session.getId(), uploadedChunks.size());
    return new InitResult(session.getId(), false, uploadedChunks);
  }

  /**
   * 接收单个分片（幂等：重复上传同索引直接成功）
   */
  public void saveChunk(String token, String uploadId, int chunkIndex, InputStream data) {
    VideoUploadSessionEntity session = loadSession(token, uploadId);
    if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
      throw new BusinessException(ErrorCode.VIDEO_CHUNK_INVALID);
    }
    Path chunkPath = chunkPath(session.getId(), chunkIndex);
    try {
      Files.createDirectories(chunkPath.getParent());
      Files.copy(data, chunkPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      log.error("分片写入失败: uploadId={}, index={}", uploadId, chunkIndex, e);
      throw new BusinessException(ErrorCode.VIDEO_CHUNK_INVALID, "分片写入失败，请重试");
    }
  }

  /**
   * 完成上传：校验分片 → 合并 → 归档对象存储 → 登记哈希（供后续秒传）
   *
   * 合并与对象存储上传不进事务（仓库规范：耗时 IO 不放事务内），
   * 数据库状态更新经 TransactionalExecutor 收敛
   */
  public String complete(String token, String uploadId, Integer durationSeconds) {
    VideoUploadSessionEntity session = loadSession(token, uploadId);

    // 秒传场景：登记表已存在同哈希文件，直接复用
    String fileKey = registryRepository.findById(session.getFileHash())
        .map(UploadedFileRegistryEntity::getFileKey)
        .orElseGet(() -> mergeAndArchive(session));

    transactionalExecutor.call(() -> {
      CandidateInterviewEntity interview = session.getInterview();
      attachVideo(interview, fileKey, session.getFileName(), session.getFileSize(),
          session.getFileHash());
      if (durationSeconds != null && durationSeconds > 0) {
        interview.setDurationSeconds(durationSeconds);
      }
      interviewRepository.save(interview);
      if (registryRepository.findById(session.getFileHash()).isEmpty()) {
        UploadedFileRegistryEntity registry = new UploadedFileRegistryEntity();
        registry.setFileHash(session.getFileHash());
        registry.setFileKey(fileKey);
        registry.setFileSize(session.getFileSize());
        registryRepository.save(registry);
      }
      return null;
    });

    cleanupStaging(session.getId());
    log.info("视频归档完成: interviewId={}, fileKey={}", session.getInterview().getId(), fileKey);
    return fileKey;
  }

  /**
   * 合并分片并归档到对象存储（无事务，纯 IO）
   */
  private String mergeAndArchive(VideoUploadSessionEntity session) {
    Path dir = stagingDir().resolve(session.getId());
    File merged = null;
    try {
      // 先校验分片完整性，再合并
      for (int i = 0; i < session.getTotalChunks(); i++) {
        if (!Files.exists(dir.resolve(String.valueOf(i)))) {
          throw new BusinessException(ErrorCode.VIDEO_CHUNK_MISSING);
        }
      }
      merged = File.createTempFile("video-merge-", ".bin");
      try (var out = Files.newOutputStream(merged.toPath())) {
        for (int i = 0; i < session.getTotalChunks(); i++) {
          Files.copy(dir.resolve(String.valueOf(i)), out);
        }
      }
      long actualSize = merged.length();
      if (actualSize != session.getFileSize()) {
        throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED,
            "合并后大小与声明不一致，请重新上传");
      }
      return fileStorageService.uploadLocalFile(merged, VIDEO_PREFIX, session.getFileName());
    } catch (IOException e) {
      log.error("视频合并失败: uploadId={}", session.getId(), e);
      throw new BusinessException(ErrorCode.VIDEO_MERGE_FAILED);
    } finally {
      if (merged != null && !merged.delete()) {
        log.warn("临时合并文件清理失败: {}", merged.getAbsolutePath());
      }
    }
  }

  private void attachVideo(CandidateInterviewEntity interview, String fileKey,
      String fileName, long fileSize, String fileHash) {
    interview.setVideoFileKey(fileKey);
    interview.setVideoFileName(fileName);
    interview.setVideoFileSize(fileSize);
    interview.setVideoHash(fileHash);
    if (interview.getStatus() == CandidateInterviewStatus.CREATED) {
      interview.setStatus(CandidateInterviewStatus.VIDEO_UPLOADED);
    }
    interview.setStartedAt(interview.getStartedAt() == null
        ? LocalDateTime.now() : interview.getStartedAt());
  }

  private List<Integer> listUploadedChunks(String uploadId) {
    Path dir = stagingDir().resolve(uploadId);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString())
          .filter(name -> name.chars().allMatch(Character::isDigit))
          .map(Integer::parseInt)
          .sorted()
          .toList();
    } catch (IOException e) {
      log.warn("读取分片目录失败: uploadId={}", uploadId, e);
      return List.of();
    }
  }

  private void cleanupStaging(String uploadId) {
    Path dir = stagingDir().resolve(uploadId);
    if (!Files.isDirectory(dir)) {
      return;
    }
    try (Stream<Path> files = Files.walk(dir)) {
      files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          log.warn("清理分片失败: {}", path, e);
        }
      });
    } catch (IOException e) {
      log.warn("清理分片目录失败: uploadId={}", uploadId, e);
    }
  }

  private VideoUploadSessionEntity loadSession(String token, String uploadId) {
    VideoUploadSessionEntity session = sessionRepository.findById(uploadId)
        .orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_UPLOAD_SESSION_NOT_FOUND));
    if (!session.getToken().equals(token)) {
      throw new BusinessException(ErrorCode.VIDEO_UPLOAD_SESSION_NOT_FOUND);
    }
    return session;
  }

  private CandidateInterviewEntity loadInterviewByToken(String token) {
    InterviewInvitationEntity invitation = invitationRepository.findByToken(token)
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_TOKEN_INVALID));
    if (invitation.getStatus() != InvitationStatus.CONFIRMED) {
      throw new BusinessException(ErrorCode.CANDIDATE_NOT_CONFIRMED);
    }
    return interviewRepository.findByInvitationId(invitation.getId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_SESSION_NOT_FOUND));
  }

  private Path chunkPath(String uploadId, int index) {
    return stagingDir().resolve(uploadId).resolve(String.valueOf(index));
  }

  private Path stagingDir() {
    String configured = properties.getStagingDir();
    Path base = (configured == null || configured.isBlank())
        ? Path.of(System.getProperty("java.io.tmpdir"), "interview-video-chunks")
        : Path.of(configured);
    try {
      Files.createDirectories(base);
    } catch (IOException e) {
      throw new UncheckedIOException("创建分片暂存目录失败", e);
    }
    return base;
  }

  /**
   * init 结果：instantUpload=true 时 uploadId 为 null 且分片列表为空
   */
  public record InitResult(String uploadId, boolean instantUpload, List<Integer> uploadedChunks) {
  }
}
