package pl.lunasoftware.demo.microservices.datagenerator.writer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;
import pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentEmployeeMatcher;
import pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Employee;
import pl.lunasoftware.demo.microservices.datagenerator.generator.EmployeeGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class DataWriter {
    private final DepartmentGenerator departmentGenerator = new DepartmentGenerator();
    private final EmployeeGenerator employeeGenerator = new EmployeeGenerator();
    private final DepartmentEmployeeMatcher matcher = new DepartmentEmployeeMatcher();

    private final SqlFileWriter sqlFileWriter = new SqlFileWriter();

    private static final int EMPLOYEES_BATCH_SIZE = 1_000;

    public void writeRandomData(int departmentsCount, int employeesCount) throws IOException {
        Path outputDir = createDir("data-generator/output");

        Path departmentsFile = outputDir.resolve("departments.sql");
        safeCreateFile(departmentsFile);

        Department[] departments = departmentGenerator.randomDepartments(departmentsCount);
        sqlFileWriter.writeDepartmentsToFile(departments, departmentsFile);
        System.out.println("Wrote " + departments.length + " departments");

        Path employeesFile = outputDir.resolve("employees.sql");
        safeCreateFile(employeesFile);

        Path employeesDepartmentsFile = outputDir.resolve("employeesDepartments.sql");
        safeCreateFile(employeesDepartmentsFile);

        int employeesGenerated = 0;
        int employeesToGenerate = employeesCount;
        while (employeesGenerated < employeesCount) {
            Employee[] employees = employeeGenerator.randomEmployees(Math.min(EMPLOYEES_BATCH_SIZE, employeesToGenerate));
            sqlFileWriter.writeEmployeesToFile(employees, employeesFile);

            Map<Employee, Department[]> employeeDepartments = matcher.assignEmployeesToDepartments(employees, departments);
            sqlFileWriter.writeEmployeesDepartmentsAssignments(employeeDepartments, employeesDepartmentsFile);

            employeesToGenerate -= employees.length;
            employeesGenerated += employees.length;
            System.out.printf("Employees generating progress: %.2f%%%n", 100 * employeesGenerated / (double) employeesCount);
        }
        System.out.println("Wrote " + employeesGenerated + " employees");
    }

    private static Path createDir(String dir) throws IOException {
        Path outputDir = Paths.get(dir);
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private static void safeCreateFile(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
        Files.createFile(path);
    }
}
