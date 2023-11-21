package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.model.Department;
import pl.lunasoftware.demo.microservices.datagenerator.model.DepartmentEmployeeMatcher;
import pl.lunasoftware.demo.microservices.datagenerator.model.DepartmentGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.model.Employee;
import pl.lunasoftware.demo.microservices.datagenerator.model.EmployeeGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqlFileWriter {
    private final DepartmentGenerator departmentGenerator = new DepartmentGenerator();
    private final EmployeeGenerator employeeGenerator = new EmployeeGenerator();
    private final DepartmentEmployeeMatcher matcher = new DepartmentEmployeeMatcher();
    private final SqlGenerator sqlGenerator = new SqlGenerator();

    public static void main(String[] args) throws IOException {
        int departments = 5_000;
        int employees = 10_000;
        if (args.length == 1) {
            try {
                employees = Integer.parseInt(args[0]);
            } catch (Exception e) {
                System.out.printf("Unable to parse argument. Using default (%d employees).%n", employees);
            }
        } else {
            System.out.printf("No arguments provided. Using default (%d employees).%n", employees);
        }

        new SqlFileWriter().writeSqlFile(departments, employees);
    }

    private void writeSqlFile(int departmentsCount, int employeesCount) throws IOException {
        Path path = findNextSqlDataFile();
        Files.createDirectories(path.getParent());
        Files.createFile(path);

        Set<Department> departments = departmentGenerator.randomDepartments(departmentsCount);
        String departmentsSql = sqlGenerator.generateDepartmentsBatchSql(departments);
        System.out.println("Departments generated: " + departmentsCount);
        Files.write(path, departmentsSql.getBytes(), StandardOpenOption.APPEND);

        int employeesBatchSize = 1_000;
        int employeesToGenerate = employeesCount;
        int employeesGenerated = 0;

        while (employeesToGenerate > 0) {
            List<Employee> employees = employeeGenerator.randomEmployees(Math.min(employeesToGenerate, employeesBatchSize));
            String employeesSql = sqlGenerator.generateEmployeesBatchSql(employees);
            Files.write(path, employeesSql.getBytes(), StandardOpenOption.APPEND);
            employeesGenerated += employees.size();

            Map<Department, List<Employee>> departmentsEmployees = matcher.assignEmployeesToDepartments(departments, employees);
            String departmentsEmployeesSql = sqlGenerator.generateDepartmentsEmployeesAssignmentSql(departmentsEmployees);
            Files.write(path, departmentsEmployeesSql.getBytes(), StandardOpenOption.APPEND);

            employeesToGenerate -= employees.size();
            System.out.printf("Employees generating progress: %.2f%%%n", 100 * employeesGenerated / (double) employeesCount);
        }

        System.out.println("Employees generated: " + employeesGenerated);
        System.out.println("Generated file: " + path);
    }

    private Path findNextSqlDataFile() {
        String pathTemplate = "data-generator/output/%03d-generated-data.sql";

        int i = 1;
        while (Files.exists(Paths.get(String.format(pathTemplate, i)))) {
            i++;
        }

        return Paths.get(String.format(pathTemplate, i));
    }
}
