CREATE TABLE department
(
    id   UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE INDEX department_name_idx ON department (name);

CREATE TABLE employee
(
    id         UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    first_name VARCHAR(100)        NOT NULL,
    last_name  VARCHAR(100)        NOT NULL,
    email      VARCHAR(100) UNIQUE NOT NULL,
    salary     NUMERIC(10, 2)      NOT NULL,
    status     VARCHAR(50)         NOT NULL
);

CREATE INDEX employee_status_idx ON employee (status) WHERE status = 'INACTIVE';

CREATE TABLE department_employee
(
    department_id UUID,
    employee_id   UUID,
    FOREIGN KEY (department_id) REFERENCES department (id),
    FOREIGN KEY (employee_id) REFERENCES employee (id),
    UNIQUE (department_id, employee_id)
);
