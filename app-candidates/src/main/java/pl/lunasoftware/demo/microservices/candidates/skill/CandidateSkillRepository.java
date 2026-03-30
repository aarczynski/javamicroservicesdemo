package pl.lunasoftware.demo.microservices.candidates.skill;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Observed(name = "candidates.db")
@Repository
public interface CandidateSkillRepository extends JpaRepository<CandidateSkillEntity, UUID> {

    List<CandidateSkillEntity> findByCandidateId(UUID candidateId);

    @Transactional
    void deleteAllByCandidateId(UUID candidateId);
}
