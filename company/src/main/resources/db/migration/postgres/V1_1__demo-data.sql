INSERT INTO department(name)
VALUES ('IT'),
       ('Finances'),
       ('Delivery'),
       ('Ops'),
       ('Management');

INSERT INTO employee(first_name, last_name, email, salary, status)
VALUES ('Joe', 'Doe', 'joe.doe@company.com', 10000.00, 'ACTIVE'),
       ('Jan', 'Nowak', 'jan.nowak@company.com', 10000.00, 'ACTIVE'),
       ('Ilona', 'Iksińska', 'ilona.iksinska@company.com', 10000.00, 'ACTIVE'),
       ('Anna', 'Wesoła', 'anna.wesola@company.com', 10000.00, 'ACTIVE'),
       ('Jan', 'Kowalski', 'jan.kowalski@company.com', 10000.00, 'ACTIVE');

INSERT INTO department_employee(department_id, employee_id)
SELECT (SELECT id FROM department WHERE name = 'IT'), (SELECT id FROM employee WHERE email = 'joe.doe@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Finances'), (SELECT id FROM employee WHERE email = 'joe.doe@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Finances'), (SELECT id FROM employee WHERE email = 'jan.nowak@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Delivery'), (SELECT id FROM employee WHERE email = 'jan.kowalski@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Delivery'), (SELECT id FROM employee WHERE email = 'anna.wesola@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Delivery'), (SELECT id FROM employee WHERE email = 'ilona.iksinska@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Ops'), (SELECT id FROM employee WHERE email = 'jan.nowak@company.com') UNION
SELECT (SELECT id FROM department WHERE name = 'Ops'), (SELECT id FROM employee WHERE email = 'jan.kowalski@company.com');
