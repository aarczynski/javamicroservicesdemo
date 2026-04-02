INSERT INTO company (name, geo_lat, geo_lon, created_at, updated_at)
VALUES ('TechCorp Poland sp. z o.o.', 52.2297, 21.0122, NOW(), NOW()),
       ('Santander Technology Kraków', 50.0647, 19.9450, NOW(), NOW()),
       ('CD Projekt S.A.', 51.1079, 17.0385, NOW(), NOW());

INSERT INTO skill (name, created_at, updated_at)
VALUES ('Communication', NOW(), NOW()),
       ('Leadership', NOW(), NOW()),
       ('Agile', NOW(), NOW()),
       ('Scrum', NOW(), NOW()),
       ('Problem Solving', NOW(), NOW());

INSERT INTO job_offer (company_id, title, description, salary_from, salary_to, currency, status, created_at, updated_at)
VALUES (
    (SELECT id FROM company WHERE name = 'TechCorp Poland sp. z o.o.'),
    'Agile Coach',
    'Help our engineering teams adopt agile practices and continuous improvement.',
    18000.00, 24000.00, 'PLN', 'ACTIVE', NOW(), NOW()
),
(
    (SELECT id FROM company WHERE name = 'Santander Technology Kraków'),
    'Engineering Manager',
    'Lead and grow a team of engineers, drive delivery and culture.',
    22000.00, 30000.00, 'PLN', 'ACTIVE', NOW(), NOW()
),
(
    (SELECT id FROM company WHERE name = 'CD Projekt S.A.'),
    'Scrum Master',
    'Facilitate scrum ceremonies and remove impediments for development teams.',
    14000.00, 19000.00, 'PLN', 'ACTIVE', NOW(), NOW()
);

INSERT INTO job_offer_employment_type (job_offer_id, employment_type)
SELECT id, 'B2B'        FROM job_offer WHERE title = 'Agile Coach'         UNION ALL
SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Agile Coach'         UNION ALL
SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Engineering Manager' UNION ALL
SELECT id, 'B2B'        FROM job_offer WHERE title = 'Scrum Master'        UNION ALL
SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Scrum Master';

INSERT INTO job_offer_skill (job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at)
SELECT
    (SELECT id FROM job_offer WHERE title = 'Agile Coach'),
    (SELECT id FROM skill WHERE name = 'Agile'),
    'SENIOR', true, 1.00, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Agile Coach'),
    (SELECT id FROM skill WHERE name = 'Communication'),
    'SENIOR', true, 0.90, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Engineering Manager'),
    (SELECT id FROM skill WHERE name = 'Leadership'),
    'LEAD', true, 1.00, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Engineering Manager'),
    (SELECT id FROM skill WHERE name = 'Communication'),
    'SENIOR', true, 0.80, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Engineering Manager'),
    (SELECT id FROM skill WHERE name = 'Problem Solving'),
    'SENIOR', false, 0.60, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Scrum Master'),
    (SELECT id FROM skill WHERE name = 'Scrum'),
    'MID', true, 1.00, NOW(), NOW()
UNION ALL
SELECT
    (SELECT id FROM job_offer WHERE title = 'Scrum Master'),
    (SELECT id FROM skill WHERE name = 'Agile'),
    'MID', true, 0.80, NOW(), NOW();
