package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import pl.lunasoftware.demo.microservices.joboffers.PostgresContainerConfig
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferMatch.RequiredSkill
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel
import spock.lang.Specification

@DataJpaTest(properties = 'spring.flyway.locations=classpath:db/migration/postgres')
@Import(PostgresContainerConfig)
@Sql('/sql/job-offer-search-data.sql')
class JobOfferRepositorySpec extends Specification {

    @Autowired
    private JobOfferRepository jobOfferRepository

    @Autowired
    private JdbcTemplate jdbcTemplate

    private static final double WARSAW_LAT = 52.2297
    private static final double WARSAW_LON = 21.0122

    def "should find candidate matches within 100km from Warsaw with B2B and Java skill"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = findMatches(
                box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['Java']
        )

        then:
        offers*.title() as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should exclude offers where salary to is below expected salary"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = findMatches(
                box, new BigDecimal('21000.00'), [EmploymentType.B2B], ['Java']
        )

        then:
        offers*.title() == ['Senior Java Developer']
    }

    def "should exclude offers outside radius"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offers = findMatches(
                box, new BigDecimal('20000.00'), [EmploymentType.EMPLOYMENT], ['Java']
        )

        then:
        offers*.title() == ['Senior Java Developer']
    }

    def "should include Krakow offer when radius is large enough"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = findMatches(
                box, new BigDecimal('20000.00'), [EmploymentType.EMPLOYMENT], ['Java']
        )

        then:
        offers*.title() as Set == ['Senior Java Developer', 'Backend Engineer'] as Set
    }

    def "should return empty when no employment type matches"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = findMatches(
                box, new BigDecimal('10000.00'), [EmploymentType.MANDATE_CONTRACT], ['Java']
        )

        then:
        offers.isEmpty()
    }

    def "should return empty when no skill matches"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 300)

        when:
        def offers = findMatches(
                box, new BigDecimal('10000.00'), [EmploymentType.B2B, EmploymentType.EMPLOYMENT], ['Cobol']
        )

        then:
        offers.isEmpty()
    }

    def "should map company, employment types and required skills of a matched offer"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        def offer = findMatches(
                box, new BigDecimal('24000.00'), [EmploymentType.EMPLOYMENT], ['Spring Boot']
        ).first()

        then:
        offer.title() == 'Senior Java Developer'
        offer.companyName() == 'Java House Warszawa sp. z o.o.'
        offer.companyGeoLat() == WARSAW_LAT
        offer.companyGeoLon() == WARSAW_LON
        offer.salaryTo() == new BigDecimal('24000.00')
        offer.requiredYearsOfExperience() == 5
        offer.requiredOfficeDaysPercentage() == 40
        offer.status() == JobOfferStatus.ACTIVE
        offer.employmentTypes() == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set
        offer.skills() == [
                new RequiredSkill('Java', SeniorityLevel.SENIOR, true, 1.0d),
                new RequiredSkill('Spring Boot', SeniorityLevel.MID, false, 0.8d)
        ]
    }

    def "should stop matching an offer once it is closed"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        jdbcTemplate.update("UPDATE job_offer SET status = 'CLOSED' WHERE title = 'Senior Java Developer'")

        then:
        findMatches(box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['Java'])*.title() == ['Full Stack Developer']
    }

    def "should match an offer once it requires a skill the candidate has"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        jdbcTemplate.update('''
                INSERT INTO job_offer_skill (job_offer_id, skill_id, required_seniority_level, mandatory, weight)
                SELECT o.id, s.id, 'JUNIOR', false, 0.30
                FROM job_offer o, skill s
                WHERE o.title = 'Senior Java Developer' AND s.name = 'React'
                ''')

        then:
        def offers = findMatches(box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['React'])
        offers*.title() as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
        offers.find { it.title() == 'Senior Java Developer' }.skills()*.name() == ['Java', 'React', 'Spring Boot']
    }

    def "should match an offer once it is offered under the candidate's employment type"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        jdbcTemplate.update('''
                INSERT INTO job_offer_employment_type (job_offer_id, employment_type)
                SELECT id, 'EMPLOYMENT' FROM job_offer WHERE title = 'Full Stack Developer'
                ''')

        then:
        def offers = findMatches(box, new BigDecimal('20000.00'), [EmploymentType.EMPLOYMENT], ['Java'])
        offers*.title() as Set == ['Senior Java Developer', 'Full Stack Developer'] as Set
    }

    def "should stop matching an offer once it is deleted"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)
        def offerId = "(SELECT id FROM job_offer WHERE title = 'Full Stack Developer')"

        when:
        jdbcTemplate.update("DELETE FROM job_offer_skill WHERE job_offer_id = $offerId")
        jdbcTemplate.update("DELETE FROM job_offer_employment_type WHERE job_offer_id = $offerId")
        jdbcTemplate.update("DELETE FROM job_offer WHERE id = $offerId")

        then:
        findMatches(box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['Java'])*.title() == ['Senior Java Developer']
    }

    def "should return the company's current name and location"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        jdbcTemplate.update('''
                UPDATE company SET name = 'Java House sp. z o.o.', geo_lat = 52.25, geo_lon = 21.0
                WHERE name = 'Java House Warszawa sp. z o.o.'
                ''')

        then:
        def offer = findMatches(box, new BigDecimal('24000.00'), [EmploymentType.EMPLOYMENT], ['Spring Boot']).first()
        offer.companyName() == 'Java House sp. z o.o.'
        offer.companyGeoLat() == 52.25d
        offer.companyGeoLon() == 21.0d
    }

    def "should match offers by a skill's current name"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)

        when:
        jdbcTemplate.update("UPDATE skill SET name = 'Spring' WHERE name = 'Spring Boot'")

        then:
        def offers = findMatches(box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['Spring'])
        offers*.title() == ['Senior Java Developer']
        offers.first().skills()*.name() == ['Java', 'Spring']
    }

    def "should match an offer imported with sync off only after the read model is rebuilt"() {
        given:
        def box = boundingBox(WARSAW_LAT, WARSAW_LON, 100)
        jdbcTemplate.execute("SET LOCAL job_offer_match.sync = 'off'")
        jdbcTemplate.update('''
                UPDATE job_offer_skill SET required_seniority_level = 'JUNIOR'
                WHERE job_offer_id = (SELECT id FROM job_offer WHERE title = 'Full Stack Developer')
                ''')

        expect:
        juniorSkillLevels(box) == []

        when:
        jdbcTemplate.execute('SELECT rebuild_job_offer_match()')

        then:
        juniorSkillLevels(box) == [SeniorityLevel.JUNIOR, SeniorityLevel.JUNIOR]
    }

    private List<SeniorityLevel> juniorSkillLevels(double[] box) {
        findMatches(box, new BigDecimal('20000.00'), [EmploymentType.B2B], ['React'])
                .collectMany { it.skills()*.requiredSeniorityLevel() }
                .findAll { it == SeniorityLevel.JUNIOR }
    }

    private List<JobOfferMatch> findMatches(double[] box, BigDecimal expectedSalary,
                                            Collection<EmploymentType> employmentTypes,
                                            Collection<String> skillNames) {
        jobOfferRepository.findCandidateMatches(
                box[0], box[1], box[2], box[3], expectedSalary,
                employmentTypes*.name() as String[], skillNames as String[]
        )
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
