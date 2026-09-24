package pl.lunasoftware.demo.microservices.joboffers.offer;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOfferEntity, UUID> {

    // Native on purpose: Hibernate never caches the HQL->SQL translation of a query with
    // collection-valued (IN :list) parameters, so the JPQL version re-translated on every
    // request — measured at ~15% of this service's CPU under load (k8s-cluster/RPS-SCALING.md).
    @NativeQuery(sqlResultSetMapping = JobOfferEntity.ID_RESULT_MAPPING, value = """
            SELECT DISTINCT o.id FROM job_offer o
            JOIN job_offer_employment_type t ON t.job_offer_id = o.id
            JOIN company c ON c.id = o.company_id
            WHERE o.status = 'ACTIVE'
            AND c.geo_lat BETWEEN :latMin AND :latMax
            AND c.geo_lon BETWEEN :lonMin AND :lonMax
            AND o.salary_to >= :expectedSalary
            AND t.employment_type IN (:employmentTypes)
            AND EXISTS (
                SELECT 1 FROM job_offer_skill jos
                JOIN skill s ON s.id = jos.skill_id
                WHERE jos.job_offer_id = o.id
                AND s.name IN (:skillNames)
            )
            """)
    List<UUID> findCandidateMatchIds(
            double latMin,
            double latMax,
            double lonMin,
            double lonMax,
            BigDecimal expectedSalary,
            Collection<String> employmentTypes,
            Collection<String> skillNames
    );

    @EntityGraph("JobOffer.withSkillsAndCompany")
    List<JobOfferEntity> findByIdIn(Collection<UUID> ids);
}
