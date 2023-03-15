INSERT INTO department(id, name)
VALUES ('31e5afab-a3b4-4bc0-9096-6e0740363156', 'IT'),
       ('a03a6cbd-46bc-4380-a92a-f455b812e90d', 'Finances'),
       ('e4013b01-fca5-450a-96b5-ada8450fe8bc', 'Delivery'),
       ('3e8137e9-a879-43b7-8fb6-4c1273d13aad', 'Ops'),
       ('278f8313-3859-4669-80c7-81f9a0baa6b7', 'Management');

INSERT INTO employee(id, first_name, last_name, email, salary)
VALUES ('77fe0b77-5bbc-44e1-bf4f-0ae62180372e', 'Joe', 'Doe', 'joe.doe@company.com', 10000.00),
       ('560456bb-31c0-49c5-8cac-1ac6321e3edc', 'Jan', 'Nowak', 'jan.nowak@company.com', 10000.00),
       ('20113ae8-e366-4ff2-96c5-0b6bd5bd6405', 'Ilona', 'Iksińska', 'ilona.iksinska@company.com', 10000.00),
       ('c6fb8912-7450-42ed-b534-76108b048de0', 'Anna', 'Wesoła', 'anna.wesola@company.com', 10000.00),
       ('495fcba4-3d1a-467c-b626-c773128dc497', 'Jan', 'Kowalski', 'jan.kowalski@company.com', 10000.00);

INSERT INTO department_employee(department_id, employee_id)
VALUES ('31e5afab-a3b4-4bc0-9096-6e0740363156', '77fe0b77-5bbc-44e1-bf4f-0ae62180372e'),
       ('a03a6cbd-46bc-4380-a92a-f455b812e90d', '77fe0b77-5bbc-44e1-bf4f-0ae62180372e'),
       ('a03a6cbd-46bc-4380-a92a-f455b812e90d', '560456bb-31c0-49c5-8cac-1ac6321e3edc'),
       ('e4013b01-fca5-450a-96b5-ada8450fe8bc', '495fcba4-3d1a-467c-b626-c773128dc497'),
       ('e4013b01-fca5-450a-96b5-ada8450fe8bc', 'c6fb8912-7450-42ed-b534-76108b048de0'),
       ('e4013b01-fca5-450a-96b5-ada8450fe8bc', '20113ae8-e366-4ff2-96c5-0b6bd5bd6405'),
       ('3e8137e9-a879-43b7-8fb6-4c1273d13aad', '560456bb-31c0-49c5-8cac-1ac6321e3edc'),
       ('3e8137e9-a879-43b7-8fb6-4c1273d13aad', '495fcba4-3d1a-467c-b626-c773128dc497');
