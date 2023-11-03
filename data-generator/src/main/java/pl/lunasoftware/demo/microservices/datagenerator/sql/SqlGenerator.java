package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.model.Department;
import pl.lunasoftware.demo.microservices.datagenerator.model.Employee;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlGenerator {

    public String generateDepartmentsBatchSql(List<Department> departments) {
        String sqlTemplate = """
                INSERT INTO department(id, name)
                VALUES %s;
                """;

        String values = departments.stream()
                .map(d -> String.format("('%s', '%s'),", d.id(), escapeSingleQuote(d.name())))
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    public String generateEmployeesBatchSql(List<Employee> employees) {
        String sqlTemplate = """
                INSERT INTO employee(id, first_name, last_name, email, salary, status)
                VALUES %s;
                """;

        String values = employees.stream()
                .map(e -> String.format("('%s', '%s', '%s', '%s', %.2f, '%s'),", e.id(), escapeSingleQuote(e.firstName()), escapeSingleQuote(e.lastName()), e.email(), e.salary(), e.status()))
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    public String generateDepartmentsEmployeesAssignmentSql(Map<Department, List<Employee>> departmentsEmployees) {
        String sqlTemplate = """
                INSERT INTO department_employee(department_id, employee_id)
                VALUES %s;
                """;

        String values = departmentsEmployees.keySet().stream()
                .flatMap(d -> departmentsEmployees.get(d).stream()
                        .map(e -> String.format("('%s', '%s'),", d.id(), e.id()))

                )
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
