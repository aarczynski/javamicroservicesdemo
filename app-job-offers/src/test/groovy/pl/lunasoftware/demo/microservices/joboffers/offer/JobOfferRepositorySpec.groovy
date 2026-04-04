package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import spock.lang.Specification

@DataJpaTest
class JobOfferRepositorySpec extends Specification {

    @Autowired
    private JobOfferRepository jobOfferRepository

    private static final double WARSAW_LAT = 52.2297
    private static final double WARSAW_LON = 21.0122

    def "should find candidate matches within 100km from Warsaw with B2B and Java skill"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('20000.00'),
                [EmploymentType.B2B],
                ['Java']
        )

        then:
        offers*.title as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should exclude offers where salary to is below expected salary"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('21000.00'),
                [EmploymentType.B2B],
                ['Java']
        )

        then:
        offers*.title == ['Senior Java Developer']
    }

    def "should exclude offers outside radius"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('20000.00'),
                [EmploymentType.EMPLOYMENT],
                ['Java']
        )

        then:
        offers*.title == ['Senior Java Developer']
    }

    def "should include Krakow offer when radius is large enough"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('20000.00'),
                [EmploymentType.EMPLOYMENT],
                ['Java']
        )

        then:
        offers*.title as Set == ['Senior Java Developer', 'Backend Engineer'] as Set
    }

    def "should return empty when no employment type matches"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('10000.00'),
                [EmploymentType.MANDATE_CONTRACT],
                ['Java']
        )

        then:
        offers.isEmpty()
    }

    def "should return empty when no skill matches"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('10000.00'),
                [EmploymentType.B2B, EmploymentType.EMPLOYMENT],
                ['Cobol']
        )

        then:
        offers.isEmpty()
    }

    def "should load offered employment types eagerly via entity graph in findCandidateMatches"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3],
                new BigDecimal('18000.00'),
                [EmploymentType.B2B],
                ['Java']
        )

        then:
        offers.every { !it.offeredEmploymentTypes.isEmpty() }
    }

    private static double[] boundingBox(double lat, double lon, double radiusKm) {
        double latMin = lat - radiusKm / 111.0
        double latMax = lat + radiusKm / 111.0
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))
        double lonMin = lon - lonDelta
        double lonMax = lon + lonDelta
        return [latMin, latMax, lonMin, lonMax] as double[]
    }
}
