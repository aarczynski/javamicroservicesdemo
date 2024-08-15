CREATE TABLE IF NOT EXISTS department
(
    id   UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS employee
(
    id         UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    first_name VARCHAR(100)        NOT NULL,
    last_name  VARCHAR(100)        NOT NULL,
    email      VARCHAR(100) UNIQUE NOT NULL,
    salary     NUMERIC(10, 2)      NOT NULL,
    status     VARCHAR(50)         NOT NULL
);

CREATE TABLE IF NOT EXISTS employee_department
(
    employee_id   UUID,
    department_id UUID,
    FOREIGN KEY (employee_id) REFERENCES employee (id),
    FOREIGN KEY (department_id) REFERENCES department (id),
    UNIQUE (employee_id, department_id)
);
