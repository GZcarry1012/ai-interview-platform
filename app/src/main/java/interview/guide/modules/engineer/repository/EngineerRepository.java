package interview.guide.modules.engineer.repository;

import interview.guide.modules.engineer.model.EngineerEntity;
import interview.guide.modules.engineer.model.EngineerStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerRepository extends JpaRepository<EngineerEntity, Long> {

  List<EngineerEntity> findByOrgIdOrderByCreatedAtDesc(Long orgId);

  List<EngineerEntity> findByStatusOrderByCreatedAtDesc(EngineerStatus status);

  List<EngineerEntity> findByOrgIdAndStatusOrderByCreatedAtDesc(
      Long orgId, EngineerStatus status);

  Optional<EngineerEntity> findByEmail(String email);

  Optional<EngineerEntity> findByPhone(String phone);
}
