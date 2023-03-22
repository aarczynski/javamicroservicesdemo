INSERT INTO department(name)
VALUES ('IT'),
       ('Finances');

INSERT INTO employee(first_name, last_name, email, salary)
VALUES ('Joe', 'Doe', 'joe.doe@company.com', 10000.00);

INSERT INTO department_employee(department_id, employee_id)
VALUES (SELECT id FROM department WHERE name = 'IT', SELECT id FROM employee WHERE email = 'joe.doe@company.com');
