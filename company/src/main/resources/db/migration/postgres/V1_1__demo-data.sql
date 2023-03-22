INSERT INTO department(name)
VALUES ('IT'),
       ('Finances'),
       ('Delivery'),
       ('Ops'),
       ('Management');

INSERT INTO employee(first_name, last_name, email, salary)
VALUES ('Joe', 'Doe', 'joe.doe@company.com', 10000.00),
       ('Jan', 'Nowak', 'jan.nowak@company.com', 10000.00),
       ('Ilona', 'Iksińska', 'ilona.iksinska@company.com', 10000.00),
       ('Anna', 'Wesoła', 'anna.wesola@company.com', 10000.00),
       ('Jan', 'Kowalski', 'jan.kowalski@company.com', 10000.00);

INSERT INTO department_employee(department_id, employee_id)
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'IT' AND e.email = 'joe.doe@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Finances' AND e.email = 'joe.doe@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Finances' AND e.email = 'jan.nowak@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Delivery' AND e.email = 'jan.kowalski@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Delivery' AND e.email = 'anna.wesola@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Delivery' AND e.email = 'ilona.iksinska@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Ops' AND e.email = 'jan.nowak@company.com' UNION
SELECT d.id, e.id FROM department d, employee e WHERE d.name = 'Ops' AND e.email = 'jan.kowalski@company.com';