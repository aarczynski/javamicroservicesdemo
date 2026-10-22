-- Read model for POST /job-offers/search: one row per active offer with its company and its skills and employment
-- types as arrays, so a search is one index-only filter plus a fetch of the matched rows instead of per-offer probes
-- into the skill and employment type tables and assembling every match on each request (k8s-cluster/RPS-SCALING.md).
-- A table kept in sync by triggers in the writing transaction rather than a materialized view, which can only be
-- refreshed as a whole and is stale between refreshes.
CREATE VIEW job_offer_match_source AS
SELECT o.id,
       o.company_id,
       c.name    AS company_name,
       c.geo_lat AS company_geo_lat,
       c.geo_lon AS company_geo_lon,
       o.title,
       o.description,
       o.salary_from,
       o.salary_to,
       o.currency,
       o.required_years_of_experience,
       o.required_office_days_percentage,
       o.status,
       et.employment_types,
       sk.skill_names,
       sk.skill_seniority_levels,
       sk.skill_mandatory,
       sk.skill_weights
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
WHERE o.status = 'ACTIVE';

CREATE TABLE job_offer_match AS
SELECT * FROM job_offer_match_source;

ALTER TABLE job_offer_match ADD PRIMARY KEY (id);
CREATE INDEX idx_job_offer_match_search ON job_offer_match (company_id, salary_to)
    INCLUDE (id, employment_types, skill_names);

-- Locks the offer so concurrent writes to the same offer rebuild its row one after another, each seeing the
-- previous one committed (NO KEY UPDATE doesn't conflict with the KEY SHARE locks foreign key checks take).
CREATE FUNCTION refresh_job_offer_match(p_job_offer_id UUID) RETURNS VOID AS
$$
BEGIN
    PERFORM 1 FROM job_offer WHERE id = p_job_offer_id FOR NO KEY UPDATE;
    DELETE FROM job_offer_match WHERE id = p_job_offer_id;
    INSERT INTO job_offer_match SELECT * FROM job_offer_match_source WHERE id = p_job_offer_id;
END
$$ LANGUAGE plpgsql;

-- Bulk loads import with the triggers off (job_offer_match.sync=off, see the *load-data scripts) and rebuild once.
CREATE FUNCTION rebuild_job_offer_match() RETURNS VOID AS
$$
BEGIN
    TRUNCATE job_offer_match;
    INSERT INTO job_offer_match SELECT * FROM job_offer_match_source;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION sync_job_offer_match_from_job_offer() RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM refresh_job_offer_match(OLD.id);
    END IF;
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.id <> OLD.id) THEN
        PERFORM refresh_job_offer_match(NEW.id);
    END IF;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION sync_job_offer_match_from_offer_detail() RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM refresh_job_offer_match(OLD.job_offer_id);
    END IF;
    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.job_offer_id <> OLD.job_offer_id) THEN
        PERFORM refresh_job_offer_match(NEW.job_offer_id);
    END IF;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION sync_job_offer_match_from_company() RETURNS TRIGGER AS
$$
BEGIN
    UPDATE job_offer_match
    SET company_name    = NEW.name,
        company_geo_lat = NEW.geo_lat,
        company_geo_lon = NEW.geo_lon
    WHERE company_id = NEW.id;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE FUNCTION sync_job_offer_match_from_skill() RETURNS TRIGGER AS
$$
BEGIN
    PERFORM refresh_job_offer_match(jos.job_offer_id) FROM job_offer_skill jos WHERE jos.skill_id = NEW.id;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER job_offer_match_sync
    AFTER INSERT OR UPDATE OR DELETE ON job_offer
    FOR EACH ROW WHEN (current_setting('job_offer_match.sync', true) IS DISTINCT FROM 'off')
EXECUTE FUNCTION sync_job_offer_match_from_job_offer();

CREATE TRIGGER job_offer_match_sync
    AFTER INSERT OR UPDATE OR DELETE ON job_offer_employment_type
    FOR EACH ROW WHEN (current_setting('job_offer_match.sync', true) IS DISTINCT FROM 'off')
EXECUTE FUNCTION sync_job_offer_match_from_offer_detail();

CREATE TRIGGER job_offer_match_sync
    AFTER INSERT OR UPDATE OR DELETE ON job_offer_skill
    FOR EACH ROW WHEN (current_setting('job_offer_match.sync', true) IS DISTINCT FROM 'off')
EXECUTE FUNCTION sync_job_offer_match_from_offer_detail();

CREATE TRIGGER job_offer_match_sync
    AFTER UPDATE OF name, geo_lat, geo_lon ON company
    FOR EACH ROW WHEN (current_setting('job_offer_match.sync', true) IS DISTINCT FROM 'off')
EXECUTE FUNCTION sync_job_offer_match_from_company();

CREATE TRIGGER job_offer_match_sync
    AFTER UPDATE OF name ON skill
    FOR EACH ROW WHEN (current_setting('job_offer_match.sync', true) IS DISTINCT FROM 'off')
EXECUTE FUNCTION sync_job_offer_match_from_skill();

-- The search filter on the domain tables, superseded by idx_job_offer_match_search.
DROP INDEX idx_job_offer_active_company_salary;
