package interview.guide.modules.videointerview.repository;

import interview.guide.modules.videointerview.model.VideoUploadSessionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoUploadSessionRepository
    extends JpaRepository<VideoUploadSessionEntity, String> {

  Optional<VideoUploadSessionEntity> findByTokenAndFileHash(String token, String fileHash);
}
