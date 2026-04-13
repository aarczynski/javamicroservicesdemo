INSERT INTO candidate (id, first_name, last_name, email, geo_lat, geo_lon, radius_km, years_of_experience, expected_salary, preferred_remote_days_percentage, created_at, updated_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Jan',  'Kowalski',  'jan.kowalski@example.com',       52.2297, 21.0122, 100.0,  8, 20000.00, 60, NOW(), NOW()),
       (gen_random_uuid(),                       'Anna', 'Nowak',     'anna.nowak@example.com',         50.0647, 19.9450,  50.0,  3, 18000.00, 40, NOW(), NOW()),
       (gen_random_uuid(),                       'Piotr','Wiśniewski','piotr.wisniewski@example.com',   52.2297, 21.0122, 200.0, 12, 25000.00, 80, NOW(), NOW());

INSERT INTO candidate_preferred_employment_type (candidate_id, employment_type)
SELECT id, 'B2B'          FROM candidate WHERE email = 'jan.kowalski@example.com' UNION ALL
SELECT id, 'EMPLOYMENT'   FROM candidate WHERE email = 'jan.kowalski@example.com' UNION ALL
SELECT id, 'B2B'          FROM candidate WHERE email = 'anna.nowak@example.com'   UNION ALL
SELECT id, 'EMPLOYMENT'   FROM candidate WHERE email = 'piotr.wisniewski@example.com';

INSERT INTO candidate_skill (candidate_id, skill_name, seniority_level, created_at, updated_at)
SELECT id, 'Communication',  'SENIOR', NOW(), NOW() FROM candidate WHERE email = 'jan.kowalski@example.com' UNION ALL
SELECT id, 'Leadership',     'SENIOR', NOW(), NOW() FROM candidate WHERE email = 'jan.kowalski@example.com' UNION ALL
SELECT id, 'Agile',          'MID',    NOW(), NOW() FROM candidate WHERE email = 'anna.nowak@example.com'   UNION ALL
SELECT id, 'Scrum',          'MID',    NOW(), NOW() FROM candidate WHERE email = 'anna.nowak@example.com'   UNION ALL
SELECT id, 'Leadership',     'LEAD',   NOW(), NOW() FROM candidate WHERE email = 'piotr.wisniewski@example.com' UNION ALL
SELECT id, 'Communication',  'SENIOR', NOW(), NOW() FROM candidate WHERE email = 'piotr.wisniewski@example.com' UNION ALL
SELECT id, 'Problem Solving','MID',    NOW(), NOW() FROM candidate WHERE email = 'piotr.wisniewski@example.com';
