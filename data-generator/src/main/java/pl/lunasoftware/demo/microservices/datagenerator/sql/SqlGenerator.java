package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Employee;

import java.util.Map;

public class SqlGenerator {

    public String generateDepartmentsBatchSql(Department[] departments) {
        String sqlTemplate = """
                INSERT INTO department(id, name) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Department d : departments) {
            sb
                    .append(String.format("('%s', '%s'),", d.id(), escapeSingleQuote(d.name())))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateEmployeesBatchSql(Employee[] employees) {
        String sqlTemplate = """
                INSERT INTO employee(id, first_name, last_name, email, salary, status) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Employee e : employees) {
            sb
                    .append(String.format("('%s', '%s', '%s', '%s', %s, '%s'),", e.id(), escapeSingleQuote(e.firstName()), escapeSingleQuote(e.lastName()), e.email(), e.salary(), e.status()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateDepartmentsEmployeesAssignmentSql(Map<Employee, Department[]> employeeDepartments) {
        String sqlTemplate = """
                INSERT INTO employee_department(employee_id, department_id) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Employee, Department[]> entry: employeeDepartments.entrySet()) {
            for (Department d : entry.getValue()) {
                sb
                        .append(String.format("('%s', '%s'),", entry.getKey().id(), d.id()))
                        .append(System.lineSeparator());
            }
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
