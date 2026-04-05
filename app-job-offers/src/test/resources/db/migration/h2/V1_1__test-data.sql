INSERT INTO company (name, geo_lat, geo_lon, created_at, updated_at)
VALUES ('TechCorp Poland sp. z o.o.', 52.2297, 21.0122, NOW(), NOW()),
       ('FinTech Kraków sp. z o.o.', 50.0647, 19.9450, NOW(), NOW());

INSERT INTO skill (name, created_at, updated_at)
VALUES ('Java', NOW(), NOW()),
       ('Spring Boot', NOW(), NOW()),
       ('React', NOW(), NOW());

INSERT INTO job_offer (company_id, title, description, salary_from, salary_to, currency, required_years_of_experience, status, created_at, updated_at)
VALUES (
    (SELECT id FROM company WHERE name = 'TechCorp Poland sp. z o.o.'),
    'Senior Java Developer',
    'Backend role focused on Java and Spring Boot.',
    18000.00, 24000.00, 'PLN', 5, 'ACTIVE', NOW(), NOW()
),
(
    (SELECT id FROM company WHERE name = 'TechCorp Poland sp. z o.o.'),
    'Full Stack Developer',
    'Full stack position with Java and React.',
    15000.00, 20000.00, 'PLN', 3, 'ACTIVE', NOW(), NOW()
),
(
    (SELECT id FROM company WHERE name = 'FinTech Kraków sp. z o.o.'),
    'Backend Engineer',
    'Backend role in a fintech environment.',
    16000.00, 22000.00, 'PLN', 3, 'ACTIVE', NOW(), NOW()
);

INSERT INTO job_offer_employment_type (job_offer_id, employment_type)
SELECT id, 'B2B'        FROM job_offer WHERE title = 'Senior Java Developer'  UNION ALL
SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Senior Java Developer'  UNION ALL
SELECT id, 'B2B'        FROM job_offer WHERE title = 'Full Stack Developer'   UNION ALL
SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Backend Engineer';

INSERT INTO job_offer_skill (job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at)
SELECT
    (SELECT id FROM job_offer WHERE title = 'Senior Java Developer'),
    (SELECT id FROM skill WHERE name = 'Java'),
    'SENIOR', true, 1.00, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Senior Java Developer'),
    (SELECT id FROM skill WHERE name = 'Spring Boot'),
    'MID', false, 0.80, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Full Stack Developer'),
    (SELECT id FROM skill WHERE name = 'Java'),
    'MID', true, 0.80, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Full Stack Developer'),
    (SELECT id FROM skill WHERE name = 'React'),
    'MID', true, 0.80, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Backend Engineer'),
    (SELECT id FROM skill WHERE name = 'Java'),
    'MID', true, 1.00, NOW(), NOW();
