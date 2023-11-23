package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Employee;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlGenerator {

    public String generateDepartmentsBatchSql(Collection<Department> departments) {
        String sqlTemplate = """
                INSERT INTO department(id, name) VALUES
                %s;
                """;

        String values = departments.stream()
                .map(d -> String.format("('%s', '%s'),", d.id(), escapeSingleQuote(d.name())))
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    public String generateEmployeesBatchSql(Collection<Employee> employees) {
        String sqlTemplate = """
                INSERT INTO employee(id, first_name, last_name, email, salary, status) VALUES
                %s;
                """;

        String values = employees.stream()
                .map(e -> String.format("('%s', '%s', '%s', '%s', %s, '%s'),", e.id(), escapeSingleQuote(e.firstName()), escapeSingleQuote(e.lastName()), e.email(), e.salary(), e.status()))
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    public String generateDepartmentsEmployeesAssignmentSql(Map<Employee, Collection<Department>> departmentsEmployees) {
        String sqlTemplate = """
                INSERT INTO employee_department(employee_id, department_id) VALUES
                %s;
                """;

        String values = departmentsEmployees.keySet().stream()
                .flatMap(e -> departmentsEmployees.get(e).stream()
                        .map(d -> String.format("('%s', '%s'),", e.id(), d.id())))
                .collect(Collectors.joining("\n"))
                .replaceAll(",$", "");

        return sqlTemplate.formatted(values);
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
