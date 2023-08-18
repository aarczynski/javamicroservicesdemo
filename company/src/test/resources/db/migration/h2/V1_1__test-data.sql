INSERT INTO department(name)
VALUES ('IT'),
       ('Finances');

INSERT INTO employee(first_name, last_name, email, salary, status)
VALUES ('Joe', 'Doe', 'joe.doe@company.com', 10000.00, 'ACTIVE'),
       ('Tom', 'Smith', 'tom.smith@company.com', 5000.00, 'INACTIVE');

INSERT INTO department_employee(department_id, employee_id)
VALUES (SELECT id FROM department WHERE name = 'IT', SELECT id FROM employee WHERE email = 'joe.doe@company.com'),
       (SELECT id FROM department WHERE name = 'Finances', SELECT id FROM employee WHERE email = 'tom.smith@company.com');
