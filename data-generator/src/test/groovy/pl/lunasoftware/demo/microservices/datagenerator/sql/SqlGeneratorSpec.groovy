package pl.lunasoftware.demo.microservices.datagenerator.sql

import pl.lunasoftware.demo.microservices.datagenerator.model.Department
import pl.lunasoftware.demo.microservices.datagenerator.model.Employee
import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.datagenerator.model.Employee.Status.ACTIVE
import static pl.lunasoftware.demo.microservices.datagenerator.model.Employee.Status.INACTIVE

class SqlGeneratorSpec extends Specification {

    private static final Employee TEST_EMPLOYEE_1 = new Employee(UUID.fromString('a35ef9ef-8634-4ab1-aca0-0d99aa6175d7'), 'Testing', 'Tester', 'testing.tester@gmail.com', BigDecimal.valueOf(10000.00), ACTIVE)
    private static final Employee TEST_EMPLOYEE_2 = new Employee(UUID.fromString('e689e87d-ad00-4fd4-a9cd-94d161ba656d'), 'Developing', 'Developer', 'developing.developer@gmail.com', BigDecimal.valueOf(10000.50), INACTIVE)
    private static final Department DEPARTMENT_1 = new Department(UUID.fromString('c5c2e4f2-ea58-42d4-a203-cbc1102a65be'), "Test Department 1")
    private static final Department DEPARTMENT_2 = new Department(UUID.fromString('d6de356f-171a-4102-8689-1d21a7d08171'), "Test Department 2")

    private SqlGenerator generator = new SqlGenerator()

    def "should return departments insert sql"() {
        given:
        def departments = [DEPARTMENT_1, DEPARTMENT_2]

        when:
        def actual = generator.generateDepartmentsBatchSql(departments)

        then:
        actual == '''\
                  |INSERT INTO department(id, name)
                  |VALUES ('c5c2e4f2-ea58-42d4-a203-cbc1102a65be', 'Test Department 1'),
                         |('d6de356f-171a-4102-8689-1d21a7d08171', 'Test Department 2');
                  '''.stripMargin().stripIndent()
    }

    def "should return employees insert sql"() {
        given:
        def employees = [TEST_EMPLOYEE_1, TEST_EMPLOYEE_2]

        when:
        def actual = generator.generateEmployeesBatchSql(employees)

        then:
        actual == '''\
                  |INSERT INTO employee(id, first_name, last_name, email, salary, status)
                  |VALUES ('a35ef9ef-8634-4ab1-aca0-0d99aa6175d7', 'Testing', 'Tester', 'testing.tester@gmail.com', 10000.00, 'ACTIVE'),
                         |('e689e87d-ad00-4fd4-a9cd-94d161ba656d', 'Developing', 'Developer', 'developing.developer@gmail.com', 10000.50, 'INACTIVE');
                  '''.stripMargin().stripIndent()
    }

    def "should return employees departments assigment sql"() {
        given:
        def departmentsEmployees = [
                (DEPARTMENT_1): [TEST_EMPLOYEE_1, TEST_EMPLOYEE_2],
                (DEPARTMENT_2): [TEST_EMPLOYEE_1],
        ]

        when:
        def actual = generator.generateDepartmentsEmployeesAssignmentSql(departmentsEmployees)
        print(actual)

        then:
        actual == '''\
                  |INSERT INTO department_employee(department_id, employee_id)
                  |VALUES ('c5c2e4f2-ea58-42d4-a203-cbc1102a65be', 'a35ef9ef-8634-4ab1-aca0-0d99aa6175d7'),
                         |('c5c2e4f2-ea58-42d4-a203-cbc1102a65be', 'e689e87d-ad00-4fd4-a9cd-94d161ba656d'),
                         |('d6de356f-171a-4102-8689-1d21a7d08171', 'a35ef9ef-8634-4ab1-aca0-0d99aa6175d7');
                  '''.stripMargin().stripIndent()
    }

    def "should escape single quote char in deparment name"() {
        given:
        def departments = [
                new Department(UUID.fromString('2204e991-8b91-46cf-82f0-c669583670d5'), "Test'department")
        ]

        when:
        def actual = generator.generateDepartmentsBatchSql(departments)

        then:
        actual == '''\
                  |INSERT INTO department(id, name)
                  |VALUES ('2204e991-8b91-46cf-82f0-c669583670d5', 'Test''department');
                  '''.stripMargin().stripIndent()
    }

    def "should escape single quote char in employee first name and last name"() {
        given:
        def employees = [
                new Employee(UUID.fromString("f142e106-e3a2-4074-b3b7-aa6facab9c69"), "First'name", "Last'name", 'firstname.lastname@gmail.com', BigDecimal.valueOf(5000.00), ACTIVE)
        ]

        when:
        def actual = generator.generateEmployeesBatchSql(employees)

        then:
        actual == '''\
                  |INSERT INTO employee(id, first_name, last_name, email, salary, status)
                  |VALUES ('f142e106-e3a2-4074-b3b7-aa6facab9c69', 'First''name', 'Last''name', 'firstname.lastname@gmail.com', 5000.00, 'ACTIVE');
                  '''.stripMargin().stripIndent()
    }
}
