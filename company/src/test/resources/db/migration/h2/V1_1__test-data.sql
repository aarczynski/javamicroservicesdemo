INSERT INTO department(id, name)
VALUES ('31e5afab-a3b4-4bc0-9096-6e0740363156', 'IT'),
       ('a03a6cbd-46bc-4380-a92a-f455b812e90d', 'Finances');

INSERT INTO employee(id, first_name, last_name, email, salary)
VALUES ('77fe0b77-5bbc-44e1-bf4f-0ae62180372e', 'Joe', 'Doe', 'joe.doe@company.com', 10000.00);

INSERT INTO department_employee(department_id, employee_id)
VALUES ('31e5afab-a3b4-4bc0-9096-6e0740363156', '77fe0b77-5bbc-44e1-bf4f-0ae62180372e');
