package pl.lunasoftware.demo.microservices.candidates.candidate;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {

    @EntityGraph("Candidate.withSkillsAndEmploymentTypes")
    Optional<CandidateEntity> findById(UUID id);
}
