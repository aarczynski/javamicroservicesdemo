package pl.lunasoftware.demo.microservices.joboffers.offer;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Observed(name = "job-offers.db")
@Repository
public interface JobOfferRepository extends JpaRepository<JobOfferEntity, UUID> {

    @EntityGraph("JobOffer.withAllRelations")
    @Query("""
            SELECT DISTINCT o FROM JobOffer o
            JOIN o.offeredEmploymentTypes t
            JOIN o.company c
            WHERE o.status = 'ACTIVE'
            AND c.geoLat BETWEEN :latMin AND :latMax
            AND c.geoLon BETWEEN :lonMin AND :lonMax
            AND o.salaryTo >= :expectedSalary
            AND t IN :employmentTypes
            AND EXISTS (
                SELECT jos FROM JobOfferSkill jos
                WHERE jos.jobOffer = o
                AND jos.skill.name IN :skillNames
            )
            """)
    List<JobOfferEntity> findCandidateMatches(
            double latMin,
            double latMax,
            double lonMin,
            double lonMax,
            BigDecimal expectedSalary,
            Collection<EmploymentType> employmentTypes,
            Collection<String> skillNames
    );
}
