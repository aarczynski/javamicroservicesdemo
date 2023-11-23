package pl.lunasoftware.demo.microservices.datagenerator.model

import pl.lunasoftware.demo.microservices.datagenerator.generator.DepartmentGenerator
import spock.lang.Specification

class DepartmentGeneratorSpec extends Specification {

    private DepartmentGenerator gen = new DepartmentGenerator()

    def "should generate random department"() {
        given:
        def count = 5000

        when:
        def actual = gen.randomDepartments(count)

        then:
        actual.size() == count
    }
}
