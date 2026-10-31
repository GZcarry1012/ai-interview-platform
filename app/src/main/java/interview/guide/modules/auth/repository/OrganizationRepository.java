package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.OrganizationEntity;
import interview.guide.modules.auth.model.OrganizationType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, Long> {

  Optional<OrganizationEntity> findByName(String name);

  Optional<OrganizationEntity> findByNameAndOrgType(String name, OrganizationType orgType);
}
