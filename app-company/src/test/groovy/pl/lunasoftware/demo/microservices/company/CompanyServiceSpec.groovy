package pl.lunasoftware.demo.microservices.company

import org.instancio.Instancio
import org.springframework.web.server.ResponseStatusException
import pl.lunasoftware.demo.microservices.company.department.DepartmentCostDto
import pl.lunasoftware.demo.microservices.company.department.DepartmentEntity
import pl.lunasoftware.demo.microservices.company.department.DepartmentRepository
import pl.lunasoftware.demo.microservices.company.department.DepartmentsCostDto
import pl.lunasoftware.demo.microservices.company.employee.EmployeeDto
import pl.lunasoftware.demo.microservices.company.employee.EmployeeEntity
import pl.lunasoftware.demo.microservices.company.employee.EmployeeRepository
import spock.lang.Specification

import static org.springframework.http.HttpStatus.NOT_FOUND

class CompanyServiceSpec extends Specification {

    private DepartmentRepository departmentRepository = Mock()
    private EmployeeRepository employeeRepository = Mock()
    private CompanyService service = new CompanyService(departmentRepository, employeeRepository)

    def "should return all departments costs dto"() {
        given:
        def entities = Instancio.ofList(DepartmentEntity).size(1).create()
        departmentRepository.findAllActiveEmployees() >> entities

        when:
        def actual = service.getAllDepartmentsCosts()

        then:
        actual == new DepartmentsCostDto(
                entities.collect {it.calculateTotalCost()}.sum() as BigDecimal,
                [new DepartmentCostDto(entities[0].name, entities[0].employees.collect {it.salary}.sum() as BigDecimal)]
        )
    }

    def "should return department cost dto"() {
        given:
        def entity = Instancio.create(DepartmentEntity)
        departmentRepository.findAllActiveEmployeesForDepartment(entity.name) >> Optional.of(entity)

        when:
        def actual = service.getDepartmentCost(entity.name)

        then:
        actual == new DepartmentCostDto(entity.name, entity.employees.collect {it.salary}.sum() as BigDecimal)
    }

    def "should throw 404 status exception when department does not exist"() {
        given:
        def departmentName = 'some-department'
        employeeRepository.findEmployeeByEmail(departmentName) >> Optional.empty()

        when:
        service.findEmployee(departmentName)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == NOT_FOUND
        ex.message.contains(departmentName)
    }

    def "should return employee dto"() {
        given:
        def entity = Instancio.create(EmployeeEntity)
        employeeRepository.findEmployeeByEmail(entity.email) >> Optional.of(entity)

        when:
        def actual = service.findEmployee(entity.email)

        then:
        actual == new EmployeeDto(
                entity.id,
                entity.firstName,
                entity.lastName,
                entity.email,
                entity.salary,
                entity.departments.collect {it.name})
    }

    def "should throw 404 status exception when employee does not exist"() {
        given:
        def email = 'some@email.com'
        employeeRepository.findEmployeeByEmail(email) >> Optional.empty()

        when:
        service.findEmployee(email)

        then:
        def ex = thrown(ResponseStatusException)
        ex.statusCode == NOT_FOUND
        ex.message.contains(email)
    }
}
