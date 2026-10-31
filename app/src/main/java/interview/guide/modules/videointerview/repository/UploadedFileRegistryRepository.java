package interview.guide.modules.videointerview.repository;

import interview.guide.modules.videointerview.model.UploadedFileRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedFileRegistryRepository
    extends JpaRepository<UploadedFileRegistryEntity, String> {
}
