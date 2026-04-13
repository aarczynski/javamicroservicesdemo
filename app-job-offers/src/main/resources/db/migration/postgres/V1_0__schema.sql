CREATE TABLE company
(
    id         UUID                         DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(255) UNIQUE NOT NULL,
    geo_lat    DOUBLE PRECISION    NOT NULL,
    geo_lon    DOUBLE PRECISION    NOT NULL,
    created_at TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE TABLE skill
(
    id         UUID                         DEFAULT gen_random_uuid() PRIMARY KEY,
    name       VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP           NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skill_name ON skill (name);
CREATE INDEX idx_company_geo ON company (geo_lat, geo_lon);

CREATE TABLE job_offer
(
    id                           UUID                  DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id                   UUID         NOT NULL,
    title                        VARCHAR(255) NOT NULL,
    description                  TEXT,
    salary_from                  NUMERIC(12, 2),
    salary_to                    NUMERIC(12, 2),
    currency                     VARCHAR(10),
    required_years_of_experience INT          NOT NULL DEFAULT 0,
    required_office_days_percentage   INT          NOT NULL DEFAULT 100,
    status                       VARCHAR(50)  NOT NULL,
    created_at                   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP    NOT NULL DEFAULT NOW(),
    FOREIGN KEY (company_id) REFERENCES company (id)
);

CREATE TABLE job_offer_employment_type
(
    job_offer_id    UUID        NOT NULL,
    employment_type VARCHAR(50) NOT NULL,
    FOREIGN KEY (job_offer_id) REFERENCES job_offer (id),
    UNIQUE (job_offer_id, employment_type)
);

CREATE INDEX idx_job_offer_company_id ON job_offer (company_id);
CREATE INDEX idx_job_offer_status_salary ON job_offer (status, salary_to);
CREATE INDEX idx_job_offer_employment_type_job_offer_id ON job_offer_employment_type (job_offer_id);
CREATE INDEX idx_job_offer_employment_type ON job_offer_employment_type (employment_type);

CREATE TABLE job_offer_skill
(
    id                       UUID                   DEFAULT gen_random_uuid() PRIMARY KEY,
    job_offer_id             UUID          NOT NULL,
    skill_id                 UUID          NOT NULL,
    required_seniority_level VARCHAR(50)   NOT NULL,
    mandatory                BOOLEAN       NOT NULL,
    weight                   NUMERIC(4, 2) NOT NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT NOW(),
    FOREIGN KEY (job_offer_id) REFERENCES job_offer (id),
    FOREIGN KEY (skill_id) REFERENCES skill (id),
    UNIQUE (job_offer_id, skill_id)
);

CREATE INDEX idx_job_offer_skill_job_offer_id ON job_offer_skill (job_offer_id);
CREATE INDEX idx_job_offer_skill_skill_id ON job_offer_skill (skill_id);
