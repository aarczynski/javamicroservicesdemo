package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Specification

@DataJpaTest
class DepartmentRepositorySpec extends Specification {

    @Autowired
    private DepartmentRepository departmentRepository

    def "should find all departments"() {
        when:
        def departments = departmentRepository.findAll()

        then:
        departments*.name as Set == ['IT', 'Finances'] as Set
    }

    def "should find department by name"() {
        when:
        def department = departmentRepository.findByNameIgnoringCase('IT')

        then:
        department.get().name == 'IT'
    }

    def "should find department by name ignoring case"() {
        when:
        def department = departmentRepository.findByNameIgnoringCase('it')

        then:
        department.get().name == 'IT'
    }
}
