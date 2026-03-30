package pl.lunasoftware.demo.microservices.candidates.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CandidateSkillRepository extends JpaRepository<CandidateSkillEntity, UUID> {

    List<CandidateSkillEntity> findByCandidateId(UUID candidateId);

    @Transactional
    void deleteAllByCandidateId(UUID candidateId);
}
