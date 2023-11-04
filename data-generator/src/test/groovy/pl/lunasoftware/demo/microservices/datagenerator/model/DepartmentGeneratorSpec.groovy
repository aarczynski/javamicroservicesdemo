package pl.lunasoftware.demo.microservices.datagenerator.model


import spock.lang.Specification

class DepartmentGeneratorSpec extends Specification {

    private DepartmentGenerator gen = new DepartmentGenerator()

    def "should generate random departments"() {
        given:
        def count = 100_000

        when:
        def actual = gen.randomDepartments(count)

        then:
        actual.size() == count
        (actual*.name() as Set).size() == count
    }
}
