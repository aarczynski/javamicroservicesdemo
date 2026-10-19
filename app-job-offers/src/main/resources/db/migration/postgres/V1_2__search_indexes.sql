-- The search filter answered from indexes alone (index-only scans): offers of the companies in the bounding box by
-- salary from this one, the skill check from the (job_offer_id, skill_id) unique constraint's index.
CREATE INDEX idx_job_offer_active_company_salary ON job_offer (company_id, salary_to) INCLUDE (id)
    WHERE status = 'ACTIVE';

-- The search's bounding box as one GiST lookup on the company's location (point <@ box) instead of a B-tree range on
-- geo_lat that filters geo_lon row by row.
CREATE INDEX idx_company_location ON company USING gist (point(geo_lon, geo_lat));

-- Superseded by idx_job_offer_active_company_salary and idx_company_location.
DROP INDEX idx_job_offer_status_salary;
DROP INDEX idx_company_geo;

-- Prefixes of the unique constraints' indexes. The planner preferred these smaller ones and then had to read every
-- matched row from the table, where the unique indexes answer the same lookup on their own.
DROP INDEX idx_job_offer_employment_type_job_offer_id;
DROP INDEX idx_job_offer_skill_job_offer_id;

-- Duplicates the index behind skill's UNIQUE (name) constraint.
DROP INDEX idx_skill_name;

-- No query filters job offers by employment type alone; four distinct values make it useless as a filter anyway.
DROP INDEX idx_job_offer_employment_type;
