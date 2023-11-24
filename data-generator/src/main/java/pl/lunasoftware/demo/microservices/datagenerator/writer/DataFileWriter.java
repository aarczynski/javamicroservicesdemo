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
import java.util.Set;

public class DataFileWriter {
    private final DepartmentGenerator departmentGenerator = new DepartmentGenerator();
    private final EmployeeGenerator employeeGenerator = new EmployeeGenerator();
    private final DepartmentEmployeeMatcher matcher = new DepartmentEmployeeMatcher();

    private final SqlFileWriter sqlFileWriter = new SqlFileWriter();
    private final JsonFileWriter jsonFileWriter = new JsonFileWriter();

    private static final int EMPLOYEES_BATCH_SIZE = 1_000;

    public void writeRandomData(int departmentsCount, int employeesCount) throws IOException {
        Path path = findNextSqlDataFile();
        Files.createDirectories(path.getParent());
        Files.createFile(path);

        Set<Department> departments = departmentGenerator.randomDepartments(departmentsCount);
        sqlFileWriter.writeDepartmentsToFile(departments, path);
        jsonFileWriter.writeToFile(departments, Paths.get("data-generator/output/departments.json"));

        int employeesGenerated = 0;
        while (employeesGenerated < employeesCount) {
            Set<Employee> employees = employeeGenerator.randomEmployees(Math.min(EMPLOYEES_BATCH_SIZE, employeesCount));
            sqlFileWriter.writeEmployeesToFile(employees, path);

            Map<Employee, Set<Department>> employeeDepartments = matcher.assignEmployeesToDepartments(employees, departments);
            sqlFileWriter.writeEmployeesDepartmentsAssignments(employeeDepartments, path);

            employeesGenerated += employees.size();
            System.out.printf("Employees generating progress: %.2f%%%n", 100 * employeesGenerated / (double) employeesCount);
        }
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
