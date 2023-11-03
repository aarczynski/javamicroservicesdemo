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

public class SqlFileWriter {
    private final DepartmentGenerator departmentGenerator = new DepartmentGenerator();
    private final EmployeeGenerator employeeGenerator = new EmployeeGenerator();
    private final DepartmentEmployeeMatcher matcher = new DepartmentEmployeeMatcher();
    private final SqlGenerator sqlGenerator = new SqlGenerator();

    public static void main(String[] args) throws IOException {
        int departments = 1_000;
        int employees = 1_000_000;
        if (args.length == 2) {
            try {
                int deps = Integer.parseInt(args[0]);
                int empls = Integer.parseInt(args[1]);
                departments = deps;
                employees = empls;
            } catch (Exception e) {
                System.out.println("Unable to parse arguments. Using defaults.");
            }
        } else {
            System.out.println("No arguments provided. Using defaults.");
        }

        new SqlFileWriter().writeSqlFile(departments, employees);
    }

    public void writeSqlFile(int departmentsCount, int employeesCount) throws IOException {
        //TODO: split into batches due to OoM
//        int departmentsBatchSize = 100;
//        int employeesBatchSize = 1000;
//        for (int d = 1; d < departmentsCount; d += departmentsBatchSize) {
//
//        }

        List<Department> departments = departmentGenerator.randomDepartments(departmentsCount);
        List<Employee> employees = employeeGenerator.randomEmployees(employeesCount);
        Map<Department, List<Employee>> departmentsEmployees = matcher.assignEmployeesToDepartments(departments, employees);

        String departmentsSql = sqlGenerator.generateDepartmentsBatchSql(departments);
        String employeesSql = sqlGenerator.generateEmployeesBatchSql(employees);
        String departmentsEmployeesSql = sqlGenerator.generateDepartmentsEmployeesAssignmentSql(departmentsEmployees);

        Path path = findNextSqlDataFile();
        Files.write(path, departmentsSql.getBytes(), StandardOpenOption.CREATE_NEW);
        Files.write(path, employeesSql.getBytes(), StandardOpenOption.APPEND);
        Files.write(path, departmentsEmployeesSql.getBytes(), StandardOpenOption.APPEND);
        System.out.println("Generated file: " + path);
    }

    private Path findNextSqlDataFile() {
        String pathTemplate = "company/src/main/resources/db/migration/postgres/V1_1_%d__generated-data.sql";

        int i = 0;
        while (Files.exists(Paths.get(String.format(pathTemplate, i)))) {
            i++;
        }

        return Paths.get(String.format(pathTemplate, i));
    }
}
