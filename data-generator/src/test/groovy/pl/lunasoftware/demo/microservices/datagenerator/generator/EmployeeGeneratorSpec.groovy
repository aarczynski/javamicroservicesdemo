package pl.lunasoftware.demo.microservices.datagenerator.generator


import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.datagenerator.generator.Employee.Status.ACTIVE
import static pl.lunasoftware.demo.microservices.datagenerator.generator.EmployeeGenerator.ACTIVE_STATUS_PROBABILITY

class EmployeeGeneratorSpec extends Specification {

    private EmployeeGenerator gen = new EmployeeGenerator()

    def "random employee email should start with full name"() {
        when:
        def actual = gen.randomEmployees(1)[0]

        then:
        actual.email().startsWith("$actual.firstName.$actual.lastName".toLowerCase())
    }

    def "should generate random employees"() {
        given:
        def count = 100_000

        when:
        def actual = gen.randomEmployees(count)

        then:
        actual.size() == count
        actual*.salary().each { assert it.scale() == 2 }
        (actual*.email() as Set).size() == count
        assertActiveAndInactiveEmployeesDistribution(actual, count)
    }

    private void assertActiveAndInactiveEmployeesDistribution(Employee[] actual, int count) {
        def tolerance = 0.95
        assert actual*.status().findAll { it == ACTIVE }.size() >= count * ACTIVE_STATUS_PROBABILITY * tolerance
    }
}
