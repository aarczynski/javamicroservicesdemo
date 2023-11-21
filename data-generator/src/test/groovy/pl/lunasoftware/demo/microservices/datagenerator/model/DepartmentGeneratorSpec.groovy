package pl.lunasoftware.demo.microservices.datagenerator.model


import spock.lang.Specification

class DepartmentGeneratorSpec extends Specification {

    private DepartmentGenerator gen = new DepartmentGenerator()

    def "should generate random department"() {
        given:
        def count = 1

        when:
        def actual = gen.randomDepartments(count)

        then:
        actual.size() == count
    }
}
