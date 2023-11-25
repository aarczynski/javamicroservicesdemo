package pl.lunasoftware.demo.microservices.datagenerator.sql

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.datagenerator.generator.Department
import pl.lunasoftware.demo.microservices.datagenerator.generator.Employee
import spock.lang.Specification

import static java.math.RoundingMode.HALF_UP
import static pl.lunasoftware.demo.microservices.datagenerator.generator.Employee.Status.ACTIVE

class SqlGeneratorSpec extends Specification {

    private static final Employee EMPLOYEE_1 = Instancio.create(Employee)
    private static final Employee EMPLOYEE_2 = Instancio.create(Employee)
    private static final Department DEPARTMENT_1 = Instancio.create(Department)
    private static final Department DEPARTMENT_2 = Instancio.create(Department)

    private SqlGenerator generator = new SqlGenerator()

    def "should return departments insert sql"() {
        given:
        def departments = [DEPARTMENT_1, DEPARTMENT_2] as Department[]

        when:
        def actual = generator.generateDepartmentsBatchSql(departments)

        then:
        actual == """\
                  INSERT INTO department(id, name) VALUES
                  ('$DEPARTMENT_1.id', '$DEPARTMENT_1.name'),
                  ('$DEPARTMENT_2.id', '$DEPARTMENT_2.name');
                  """.stripIndent()
    }

    def "should return employees insert sql"() {
        given:
        def employees = [EMPLOYEE_1, EMPLOYEE_2] as Employee[]

        when:
        def actual = generator.generateEmployeesBatchSql(employees)

        then:
        actual == """\
                  INSERT INTO employee(id, first_name, last_name, email, salary, status) VALUES
                  ('$EMPLOYEE_1.id', '$EMPLOYEE_1.firstName', '$EMPLOYEE_1.lastName', '$EMPLOYEE_1.email', $EMPLOYEE_1.salary, '$EMPLOYEE_1.status'),
                  ('$EMPLOYEE_2.id', '$EMPLOYEE_2.firstName', '$EMPLOYEE_2.lastName', '$EMPLOYEE_2.email', $EMPLOYEE_2.salary, '$EMPLOYEE_2.status');
                  """.stripIndent()
    }

    def "should return employees departments assigment sql"() {
        given:
        def employeeDepartments = [
                (EMPLOYEE_1): [DEPARTMENT_1, DEPARTMENT_2] as Department[],
                (EMPLOYEE_2): [DEPARTMENT_1] as Department[],
        ]

        when:
        def actual = generator.generateDepartmentsEmployeesAssignmentSql(employeeDepartments)
        print(actual)

        then:
        actual == """\
                  INSERT INTO employee_department(employee_id, department_id) VALUES
                  ('$EMPLOYEE_1.id', '$DEPARTMENT_1.id'),
                  ('$EMPLOYEE_1.id', '$DEPARTMENT_2.id'),
                  ('$EMPLOYEE_2.id', '$DEPARTMENT_1.id');
                  """.stripIndent()
    }

    def "should escape single quote char in department name"() {
        given:
        def departments = [
                new Department(UUID.fromString('2204e991-8b91-46cf-82f0-c669583670d5'), "Test'department")
        ] as Department[]

        when:
        def actual = generator.generateDepartmentsBatchSql(departments)

        then:
        actual == '''\
                  INSERT INTO department(id, name) VALUES
                  ('2204e991-8b91-46cf-82f0-c669583670d5', 'Test''department');
                  '''.stripIndent()
    }

    def "should escape single quote char in employee first name and last name"() {
        given:
        def employees = [
                new Employee(UUID.fromString("f142e106-e3a2-4074-b3b7-aa6facab9c69"), "First'name", "Last'name", 'firstname.lastname@gmail.com', BigDecimal.valueOf(5000.00).setScale(2, HALF_UP), ACTIVE)
        ] as Employee[]

        when:
        def actual = generator.generateEmployeesBatchSql(employees)

        then:
        actual == '''\
                  INSERT INTO employee(id, first_name, last_name, email, salary, status) VALUES
                  ('f142e106-e3a2-4074-b3b7-aa6facab9c69', 'First''name', 'Last''name', 'firstname.lastname@gmail.com', 5000.00, 'ACTIVE');
                  '''.stripIndent()
    }
}
