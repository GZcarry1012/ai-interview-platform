package interview.guide.modules.engineer.repository;

import interview.guide.modules.engineer.model.EngineerAuditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineerAuditLogRepository extends JpaRepository<EngineerAuditLogEntity, Long> {

  List<EngineerAuditLogEntity> findByEngineerIdOrderByCreatedAtDesc(Long engineerId);
}
