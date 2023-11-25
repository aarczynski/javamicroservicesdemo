package pl.lunasoftware.demo.microservices.datagenerator.generator

import org.instancio.Instancio
import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentEmployeeMatcher.MAX_EMPLOYEES_PER_DEPARTMENT
import static pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentEmployeeMatcher.MIN_EMPLOYEES_PER_DEPARTMENT

class DepartmentEmployeeMatcherSpec extends Specification {

    private DepartmentEmployeeMatcher matcher = new DepartmentEmployeeMatcher()

    def "should match employees with departments"() {
        given:
        def departments = Instancio.ofList(Department).size(5).create().toArray() as Department[]
        def employees = Instancio.ofList(Employee).size(3).create().toArray() as Employee[]

        when:
        def actual = matcher.assignEmployeesToDepartments(employees, departments)

        then:
        actual.size() == 3
        actual.each {
            assert it.value.size() >= MIN_EMPLOYEES_PER_DEPARTMENT && it.value.size() <= MAX_EMPLOYEES_PER_DEPARTMENT
        }
    }
}
