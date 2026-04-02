CREATE TABLE candidate
(
    id                UUID DEFAULT random_uuid() PRIMARY KEY,
    first_name        VARCHAR(255)    NOT NULL,
    last_name         VARCHAR(255)    NOT NULL,
    email             VARCHAR(255)    UNIQUE NOT NULL,
    geo_lat           DOUBLE PRECISION NOT NULL,
    geo_lon           DOUBLE PRECISION NOT NULL,
    radius_km         DOUBLE PRECISION NOT NULL,
    expected_salary   NUMERIC(12, 2)  NOT NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE candidate_preferred_employment_type
(
    candidate_id    UUID        NOT NULL,
    employment_type VARCHAR(50) NOT NULL,
    FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    UNIQUE (candidate_id, employment_type)
);

CREATE TABLE candidate_skill
(
    id              UUID DEFAULT random_uuid() PRIMARY KEY,
    candidate_id    UUID         NOT NULL,
    skill_name      VARCHAR(255) NOT NULL,
    seniority_level VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    UNIQUE (candidate_id, skill_name)
);
