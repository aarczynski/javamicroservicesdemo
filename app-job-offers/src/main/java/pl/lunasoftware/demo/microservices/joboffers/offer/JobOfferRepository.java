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
    // The bounding box is one GiST lookup on the company's location (V1_2). The inner select is answered from indexes
    // alone (index-only scans, V1_2) and checks skills only — employment types rarely rule an offer out, so they are
    // checked on the aggregated matches instead of probed per offer.
    // Whole rows and the skill/employment type arrays scoring needs are assembled for the matches alone.
    @NativeQuery(sqlResultSetMapping = JobOfferEntity.MATCH_RESULT_MAPPING, value = """
            SELECT o.id, o.company_id, c.name AS company_name, c.geo_lat AS company_geo_lat, c.geo_lon AS company_geo_lon,
                   o.title, o.description, o.salary_from, o.salary_to, o.currency,
                   o.required_years_of_experience, o.required_office_days_percentage, o.status,
                   et.employment_types, sk.skill_names, sk.skill_seniority_levels, sk.skill_mandatory, sk.skill_weights
            FROM job_offer o
            JOIN company c ON c.id = o.company_id
            CROSS JOIN LATERAL (
                SELECT array_agg(t.employment_type::text ORDER BY t.employment_type) AS employment_types
                FROM job_offer_employment_type t
                WHERE t.job_offer_id = o.id
            ) et
            CROSS JOIN LATERAL (
                SELECT array_agg(s.name::text ORDER BY s.name)                       AS skill_names,
                       array_agg(jos.required_seniority_level::text ORDER BY s.name) AS skill_seniority_levels,
                       array_agg(jos.mandatory ORDER BY s.name)                      AS skill_mandatory,
                       array_agg(jos.weight::float8 ORDER BY s.name)                 AS skill_weights
                FROM job_offer_skill jos
                JOIN skill s ON s.id = jos.skill_id
                WHERE jos.job_offer_id = o.id
            ) sk
            WHERE o.id IN (
                SELECT f.id FROM job_offer f
                JOIN company fc ON fc.id = f.company_id
                WHERE f.status = 'ACTIVE'
                AND point(fc.geo_lon, fc.geo_lat) <@ box(point(:lonMin, :latMin), point(:lonMax, :latMax))
                AND f.salary_to >= :expectedSalary
                AND EXISTS (
                    SELECT 1 FROM job_offer_skill fs
                    WHERE fs.job_offer_id = f.id
                    AND fs.skill_id = ANY(ARRAY(SELECT s.id FROM skill s WHERE s.name = ANY(CAST(:skillNames AS text[]))))
                )
            )
            AND et.employment_types && CAST(:employmentTypes AS text[])
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
