package interview.guide.modules.invitation.repository;

import interview.guide.modules.invitation.model.InvitationStatus;
import interview.guide.modules.invitation.model.InterviewInvitationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewInvitationRepository
    extends JpaRepository<InterviewInvitationEntity, Long> {

  Optional<InterviewInvitationEntity> findByToken(String token);

  List<InterviewInvitationEntity> findByEngineerIdOrderByCreatedAtDesc(Long engineerId);

  boolean existsByEngineerIdAndStatusNot(Long engineerId, InvitationStatus status);

  List<InterviewInvitationEntity> findByEngineerIdInAndStatusNot(
      Collection<Long> engineerIds, InvitationStatus status);

  long countByEngineerIdInAndStatusNot(Collection<Long> engineerIds, InvitationStatus status);
}
