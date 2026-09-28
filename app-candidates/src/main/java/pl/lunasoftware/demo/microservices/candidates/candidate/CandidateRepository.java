package pl.lunasoftware.demo.microservices.candidates.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateRepository extends JpaRepository<CandidateEntity, UUID> {

    // JOIN FETCH instead of @EntityGraph on purpose: Hibernate never caches the load plan of a
    // query with an applied entity graph, so it was rebuilt on every request — measured at ~10%
    // of this service's CPU under load (k8s-cluster/RPS-SCALING.md).
    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN FETCH c.skills
            LEFT JOIN FETCH c.preferredEmploymentTypes
            WHERE c.id = :id
            """)
    Optional<CandidateEntity> findWithSkillsAndEmploymentTypesById(UUID id);
}
