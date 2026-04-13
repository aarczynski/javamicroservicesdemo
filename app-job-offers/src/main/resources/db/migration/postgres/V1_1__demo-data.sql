INSERT INTO company (id, name, geo_lat, geo_lon, created_at, updated_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'TechCorp Poland sp. z o.o.', 52.2297, 21.0122, NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000002', 'Santander Technology Kraków', 50.0647, 19.9450, NOW(), NOW()),
       ('a0000000-0000-0000-0000-000000000003', 'CD Projekt S.A.', 51.1079, 17.0385, NOW(), NOW());

INSERT INTO skill (id, name, created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000001', 'Communication', NOW(), NOW()),
       ('b0000000-0000-0000-0000-000000000002', 'Leadership', NOW(), NOW()),
       ('b0000000-0000-0000-0000-000000000003', 'Agile', NOW(), NOW()),
       ('b0000000-0000-0000-0000-000000000004', 'Scrum', NOW(), NOW()),
       ('b0000000-0000-0000-0000-000000000005', 'Problem Solving', NOW(), NOW());

INSERT INTO job_offer (id, company_id, title, description, salary_from, salary_to, currency, required_years_of_experience, required_office_days_percentage, status, created_at, updated_at)
VALUES ('c0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
        'Agile Coach', 'Help our engineering teams adopt agile practices and continuous improvement.',
        18000.00, 24000.00, 'PLN', 5, 40, 'ACTIVE', NOW(), NOW()),
       ('c0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002',
        'Engineering Manager', 'Lead and grow a team of engineers, drive delivery and culture.',
        22000.00, 30000.00, 'PLN', 8, 80, 'ACTIVE', NOW(), NOW()),
       ('c0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003',
        'Scrum Master', 'Facilitate scrum ceremonies and remove impediments for development teams.',
        14000.00, 19000.00, 'PLN', 2, 20, 'ACTIVE', NOW(), NOW());

INSERT INTO job_offer_employment_type (job_offer_id, employment_type)
VALUES ('c0000000-0000-0000-0000-000000000001', 'B2B'),
       ('c0000000-0000-0000-0000-000000000001', 'EMPLOYMENT'),
       ('c0000000-0000-0000-0000-000000000002', 'EMPLOYMENT'),
       ('c0000000-0000-0000-0000-000000000003', 'B2B'),
       ('c0000000-0000-0000-0000-000000000003', 'EMPLOYMENT');

INSERT INTO job_offer_skill (id, job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at)
VALUES ('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003', 'SENIOR', true, 1.00, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'SENIOR', true, 0.90, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'LEAD',   true, 1.00, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'SENIOR', true, 0.80, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000005', 'SENIOR', false, 0.60, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000004', 'MID',    true, 1.00, NOW(), NOW()),
       ('d0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', 'MID',    true, 0.80, NOW(), NOW());
