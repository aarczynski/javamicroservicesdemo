package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate

import spock.lang.Specification

class CandidateGeneratorSpec extends Specification {

    private def gen = new CandidateGenerator()

    def "random candidate email should start with full name"() {
        when:
        def actual = gen.randomCandidates(1)[0]

        then:
        actual.email().startsWith((actual.firstName() + '.' + actual.lastName()).toLowerCase())
    }

    def "should generate random candidates"() {
        given:
        int count = 1_000

        when:
        def actual = gen.randomCandidates(count)

        then:
        actual.length == count
        actual.every { it.id() != null }
        actual.every { it.expectedSalary().scale() == 2 }
        actual.collect { it.email() }.toSet().size() == count
        actual.every { it.preferredEmploymentTypes() != null && it.preferredEmploymentTypes().length >= 1 }
        actual.every { it.geoLat() >= -90.0 && it.geoLat() <= 90.0 }
        actual.every { it.geoLon() >= -180.0 && it.geoLon() <= 180.0 }
        actual.every { it.radiusKm() >= 10.0 && it.radiusKm() <= 200.0 }
    }
}
