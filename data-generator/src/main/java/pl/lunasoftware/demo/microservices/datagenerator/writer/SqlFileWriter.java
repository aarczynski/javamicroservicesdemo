package pl.lunasoftware.demo.microservices.datagenerator.writer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Employee;
import pl.lunasoftware.demo.microservices.datagenerator.sql.SqlGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;

public class SqlFileWriter {
    private final SqlGenerator sqlGenerator = new SqlGenerator();

    public void writeDepartmentsToFile(Collection<Department> departments, Path path) throws IOException {
        String departmentsSql = sqlGenerator.generateDepartmentsBatchSql(departments);
        Files.write(path, departmentsSql.getBytes(), StandardOpenOption.APPEND);
    }

    public void writeEmployeesToFile(Collection<Employee> employees, Path path) throws IOException {
        String departmentsSql = sqlGenerator.generateEmployeesBatchSql(employees);
        Files.write(path, departmentsSql.getBytes(), StandardOpenOption.APPEND);
    }

    public void writeEmployeesDepartmentsAssignments(Map<Employee, Collection<Department>> employeeDepartments, Path path) throws IOException {
        String departmentsSql = sqlGenerator.generateDepartmentsEmployeesAssignmentSql(employeeDepartments);
        Files.write(path, departmentsSql.getBytes(), StandardOpenOption.APPEND);
    }
}
