package pl.lunasoftware.demo.microservices.joboffers.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOfferEntity, UUID> {

    // Native on purpose: Postgres array parameters instead of IN (:list) keep the SQL text identical for every
    // candidate, so both Hibernate and Postgres reuse the prepared statement and its plan (k8s-cluster/RPS-SCALING.md).
    // Reads the job_offer_match read model (V1_3). The bounding box is one GiST lookup on the company's location, the
    // inner select is answered from idx_job_offer_match_search alone (index-only scan), and the outer one fetches whole
    // rows for the matches only.
    @NativeQuery(sqlResultSetMapping = JobOfferEntity.MATCH_RESULT_MAPPING, value = """
            SELECT m.id, m.company_id, m.company_name, m.company_geo_lat, m.company_geo_lon,
                   m.title, m.description, m.salary_from, m.salary_to, m.currency,
                   m.required_years_of_experience, m.required_office_days_percentage, m.status,
                   m.employment_types, m.skill_names, m.skill_seniority_levels, m.skill_mandatory, m.skill_weights
            FROM job_offer_match m
            WHERE m.id IN (
                SELECT f.id FROM job_offer_match f
                JOIN company c ON c.id = f.company_id
                WHERE point(c.geo_lon, c.geo_lat) <@ box(point(:lonMin, :latMin), point(:lonMax, :latMax))
                AND f.salary_to >= :expectedSalary
                AND f.employment_types && CAST(:employmentTypes AS text[])
                AND f.skill_names && CAST(:skillNames AS text[])
            )
            """)
    List<JobOfferMatch> findCandidateMatches(
            double latMin,
            double latMax,
            double lonMin,
            double lonMax,
            BigDecimal expectedSalary,
            String[] employmentTypes,
            String[] skillNames
    );
}
