package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import pl.lunasoftware.demo.microservices.joboffers.company.CompanyRepository
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillRepository
import spock.lang.Specification

@DataJpaTest
class JobOfferRepositorySpec extends Specification {

    @Autowired
    private JobOfferRepository jobOfferRepository

    @Autowired
    private CompanyRepository companyRepository

    @Autowired
    private SkillRepository skillRepository

    // Warsaw coordinates (same as test data)
    static final double WARSAW_LAT = 52.2297
    static final double WARSAW_LON = 21.0122

    def "should find all active offers"() {
        when:
        def offers = jobOfferRepository.findByStatus(JobOfferStatus.ACTIVE)

        then:
        offers.size() == 3
        offers*.status.every { it == JobOfferStatus.ACTIVE }
    }

    def "should find offers by company"() {
        given:
        def company = companyRepository.findByName('TechCorp Poland sp. z o.o.').get()

        when:
        def offers = jobOfferRepository.findByCompanyId(company.id)

        then:
        offers.size() == 2
        offers*.title as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should find employment types for offer"() {
        when:
        def offer = jobOfferRepository.findByStatus(JobOfferStatus.ACTIVE)
                .find { it.title == 'Senior Java Developer' }

        then:
        offer.offeredEmploymentTypes == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set
    }

    def "should find candidate matches within 100km from Warsaw with B2B and Java skill"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.B2B],
                [javaSkill.id]
        )

        then: "both Warsaw B2B offers with Java skill and salaryTo >= 20000 are returned"
        offers*.title as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should exclude offers where salaryTo is below expected salary"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                21000.00,
                [EmploymentType.B2B],
                [javaSkill.id]
        )

        then: "Full Stack Developer (salaryTo=20000) is excluded"
        offers*.title as Set == ['Senior Java Developer'] as Set
    }

    def "should exclude offers outside radius"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.EMPLOYMENT],
                [javaSkill.id]
        )

        then: "Backend Engineer (Kraków, ~250km away) is outside bounding box"
        offers*.title as Set == ['Senior Java Developer'] as Set
    }

    def "should include Krakow offer when radius is large enough"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                20000.00,
                [EmploymentType.EMPLOYMENT],
                [javaSkill.id]
        )

        then: "Senior Java Developer (Warsaw) and Backend Engineer (Kraków) both match"
        offers*.title as Set == ['Senior Java Developer', 'Backend Engineer'] as Set
    }

    def "should return empty when no employment type matches"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                10000.00,
                [EmploymentType.MANDATE_CONTRACT],
                [javaSkill.id]
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
                [UUID.randomUUID()]
        )

        then:
        offers.isEmpty()
    }

    def "should load offeredEmploymentTypes eagerly via entity graph in findCandidateMatches"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('Java').get()
        def (latMin, latMax, lonMin, lonMax) = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = jobOfferRepository.findCandidateMatches(
                latMin, latMax, lonMin, lonMax,
                18000.00,
                [EmploymentType.B2B],
                [javaSkill.id]
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
