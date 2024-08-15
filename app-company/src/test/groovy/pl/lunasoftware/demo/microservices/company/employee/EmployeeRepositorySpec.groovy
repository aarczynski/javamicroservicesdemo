package pl.lunasoftware.demo.microservices.company.employee

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Specification

@DataJpaTest
class EmployeeRepositorySpec extends Specification {

    @Autowired
    private EmployeeRepository repository

    def "should find employee by email"() {
        given:
        def email = 'joe.doe@company.com'

        when:
        def actual = repository.findEmployeeByEmail(email)

        then:
        with (actual.get()) {
            firstName == 'Joe'
            lastName == 'Doe'
        }
    }
}
