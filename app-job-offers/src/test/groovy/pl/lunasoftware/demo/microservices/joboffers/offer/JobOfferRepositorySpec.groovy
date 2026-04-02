package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferRepository
import spock.lang.Specification

@DataJpaTest
class JobOfferRepositorySpec extends Specification {

    @Autowired
    private JobOfferRepository jobOfferRepository

    static final double WARSAW_LAT = 52.2297
    static final double WARSAW_LON = 21.0122

    def "should find candidate matches within 100km from Warsaw with B2B and Java skill"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.B2B],
                ['Java']
        )

        then: "both Warsaw B2B offers with Java skill and salaryTo >= 20000 are returned"
        offers*.title as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should exclude offers where salaryTo is below expected salary"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                21000.00,
                [EmploymentType.B2B],
                ['Java']
        )

        then: "Full Stack Developer (salaryTo=20000) is excluded"
        offers*.title as Set == ['Senior Java Developer'] as Set
    }

    def "should exclude offers outside radius"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.EMPLOYMENT],
                ['Java']
        )

        then: "Backend Engineer (Kraków, ~250km away) is outside bounding box"
        offers*.title as Set == ['Senior Java Developer'] as Set
    }

    def "should include Krakow offer when radius is large enough"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.EMPLOYMENT],
                ['Java']
        )

        then: "Senior Java Developer (Warsaw) and Backend Engineer (Kraków) both match"
        offers*.title as Set == ['Senior Java Developer', 'Backend Engineer'] as Set
    }

    def "should return empty when no employment type matches"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                10000.00,
                [EmploymentType.MANDATE_CONTRACT],
                ['Java']
        )

        then:
        offers.isEmpty()
    }

    def "should return empty when no skill matches"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                10000.00,
                [EmploymentType.B2B, EmploymentType.EMPLOYMENT],
                ['Cobol']
        )

        then:
        offers.isEmpty()
    }

    def "should load offeredEmploymentTypes eagerly via entity graph in findCandidateMatches"() {
        given:
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                18000.00,
                [EmploymentType.B2B],
                ['Java']
        )

        then: "offeredEmploymentTypes is initialized (no LazyInitializationException)"
        offers.every { !it.offeredEmploymentTypes.isEmpty() }
    }

    private static List<Double> boundingBox(double lat, double lon, double radiusKm) {
        double latMin = lat - radiusKm / 111.0
        double latMax = lat + radiusKm / 111.0
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))
        double lonMin = lon - lonDelta
        double lonMax = lon + lonDelta
        [latMin, latMax, lonMin, lonMax]
    }
}
