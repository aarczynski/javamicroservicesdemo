package pl.lunasoftware.demo.microservices.datagenerator.generator.company

import spock.lang.Specification

class CompanyGeneratorSpec extends Specification {

    private def gen = new CompanyGenerator()

    def "should generate random companies with unique names"() {
        given:
        int count = 100

        when:
        def actual = gen.randomCompanies(count)

        then:
        actual.length == count
        actual.collect { it.name() }.toSet().size() == count
    }

    def "should generate companies with geo coordinates within valid range"() {
        when:
        def actual = gen.randomCompanies(50)

        then:
        actual.every { it.geoLat() >= -90.0 && it.geoLat() <= 90.0 }
        actual.every { it.geoLon() >= -180.0 && it.geoLon() <= 180.0 }
    }
}
