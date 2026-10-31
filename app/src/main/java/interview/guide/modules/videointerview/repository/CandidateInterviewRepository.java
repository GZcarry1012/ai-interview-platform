package interview.guide.modules.videointerview.repository;

import interview.guide.modules.videointerview.model.CandidateInterviewEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateInterviewRepository extends JpaRepository<CandidateInterviewEntity, Long> {

  Optional<CandidateInterviewEntity> findByToken(String token);

  Optional<CandidateInterviewEntity> findByInvitationId(Long invitationId);
}
