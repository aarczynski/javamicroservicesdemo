package pl.lunasoftware.demo.microservices.datagenerator.sql

import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOffer
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssignment
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferStatus
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill
import spock.lang.Specification

import java.math.RoundingMode

class JobOffersSqlGeneratorSpec extends Specification {

    private static final UUID COMPANY_ID_1 = UUID.fromString('11111111-1111-1111-1111-111111111111')
    private static final UUID COMPANY_ID_2 = UUID.fromString('22222222-2222-2222-2222-222222222222')
    private static final UUID SKILL_ID_1 = UUID.fromString('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')
    private static final UUID SKILL_ID_2 = UUID.fromString('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb')
    private static final UUID JOB_OFFER_ID_1 = UUID.fromString('cccccccc-cccc-cccc-cccc-cccccccccccc')
    private static final UUID JOB_OFFER_ID_2 = UUID.fromString('dddddddd-dddd-dddd-dddd-dddddddddddd')
    private static final UUID ASSIGNMENT_ID_1 = UUID.fromString('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee')

    private static final Company COMPANY_1 = new Company(COMPANY_ID_1, 'Acme Corp', 52.123456, 21.098765)
    private static final Company COMPANY_2 = new Company(COMPANY_ID_2, "O'Brien & Sons", -33.867, 151.207)
    private static final Skill SKILL_1 = new Skill(SKILL_ID_1, 'Java')
    private static final Skill SKILL_2 = new Skill(SKILL_ID_2, 'Python')

    private def generator = new JobOffersSqlGenerator()

    def "should return companies insert SQL"() {
        given:
        def companies = [COMPANY_1, COMPANY_2] as Company[]

        when:
        def actual = generator.generateCompaniesBatchSql(companies)

        then:
        actual == """\
                |INSERT INTO company(id, name, geo_lat, geo_lon, created_at, updated_at) VALUES
                |('$COMPANY_ID_1', 'Acme Corp', 52.123456, 21.098765, NOW(), NOW()),
                |('$COMPANY_ID_2', 'O''Brien & Sons', -33.867, 151.207, NOW(), NOW());
                |""".stripMargin()
    }

    def "should return skills insert SQL"() {
        given:
        def skills = [SKILL_1, SKILL_2] as Skill[]

        when:
        def actual = generator.generateSkillsBatchSql(skills)

        then:
        actual == """\
                |INSERT INTO skill(id, name, created_at, updated_at) VALUES
                |('$SKILL_ID_1', 'Java', NOW(), NOW()),
                |('$SKILL_ID_2', 'Python', NOW(), NOW());
                |""".stripMargin()
    }

    def "should return job offers insert SQL"() {
        given:
        def offer = new JobOffer(
                JOB_OFFER_ID_1, COMPANY_ID_1, 'Software Engineer', 'Great job opportunity.',
                BigDecimal.valueOf(8000.00).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(12000.00).setScale(2, RoundingMode.HALF_UP),
                'PLN', 5, 60, JobOfferStatus.ACTIVE,
                [EmploymentType.B2B] as EmploymentType[])
        def jobOffers = [offer] as JobOffer[]

        when:
        def actual = generator.generateJobOffersBatchSql(jobOffers)

        then:
        actual == """\
                |INSERT INTO job_offer(id, company_id, title, description, salary_from, salary_to, currency, required_years_of_experience, required_office_days_percentage, status, created_at, updated_at) VALUES
                |('$JOB_OFFER_ID_1', '$COMPANY_ID_1', 'Software Engineer', 'Great job opportunity.', 8000.00, 12000.00, 'PLN', 5, 60, 'ACTIVE', NOW(), NOW());
                |""".stripMargin()
    }

    def "should return job offer employment types insert SQL"() {
        given:
        def offer1 = new JobOffer(JOB_OFFER_ID_1, COMPANY_ID_1, 'Dev', 'Desc',
                BigDecimal.TEN, BigDecimal.TEN, 'PLN', 3, 40, JobOfferStatus.ACTIVE,
                [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as EmploymentType[])
        def offer2 = new JobOffer(JOB_OFFER_ID_2, COMPANY_ID_2, 'Lead', 'Desc',
                BigDecimal.TEN, BigDecimal.TEN, 'PLN', 8, 80, JobOfferStatus.DRAFT,
                [EmploymentType.MANDATE_CONTRACT] as EmploymentType[])
        def jobOffers = [offer1, offer2] as JobOffer[]

        when:
        def actual = generator.generateJobOfferEmploymentTypesBatchSql(jobOffers)

        then:
        actual.startsWith('INSERT INTO job_offer_employment_type(job_offer_id, employment_type) VALUES\n')
        actual.contains("'$JOB_OFFER_ID_1', 'B2B'")
        actual.contains("'$JOB_OFFER_ID_1', 'EMPLOYMENT'")
        actual.contains("'$JOB_OFFER_ID_2', 'MANDATE_CONTRACT'")
    }

    def "should return job offer skill assignments insert SQL"() {
        given:
        def assignment = new JobOfferSkillAssignment(
                ASSIGNMENT_ID_1, JOB_OFFER_ID_1, SKILL_ID_1,
                SeniorityLevel.SENIOR, true,
                BigDecimal.valueOf(0.80).setScale(2, RoundingMode.HALF_UP))
        def assignments = [assignment] as JobOfferSkillAssignment[]

        when:
        def actual = generator.generateJobOfferSkillAssignmentsBatchSql(assignments)

        then:
        actual == """\
                |INSERT INTO job_offer_skill(id, job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at) VALUES
                |('$ASSIGNMENT_ID_1', '$JOB_OFFER_ID_1', '$SKILL_ID_1', 'SENIOR', true, 0.80, NOW(), NOW());
                |""".stripMargin()
    }

    def "should escape single quote in company name"() {
        given:
        def companies = [COMPANY_2] as Company[]

        when:
        def actual = generator.generateCompaniesBatchSql(companies)

        then:
        actual.contains("O''Brien & Sons")
    }

    def "should produce multiple INSERT statements when row count exceeds chunk size"() {
        given:
        def companies = (1..5001).collect { i ->
            new Company(UUID.randomUUID(), "Company $i", 0.0, 0.0)
        } as Company[]

        when:
        def actual = generator.generateCompaniesBatchSql(companies)

        then:
        actual.split('INSERT INTO company').length - 1 == 2
    }

    def "should include all rows across chunks"() {
        given:
        def ids = (1..5001).collect { UUID.randomUUID() }
        def companies = ids.collect { id ->
            new Company(id, 'Corp', 0.0, 0.0)
        } as Company[]

        when:
        def actual = generator.generateCompaniesBatchSql(companies)

        then:
        ids.every { id -> actual.contains(id.toString()) }
    }

    def "should terminate each chunk with a semicolon and not leave dangling commas between chunks"() {
        given:
        def companies = (1..5001).collect { i ->
            new Company(UUID.randomUUID(), "Company $i", 0.0, 0.0)
        } as Company[]

        when:
        def actual = generator.generateCompaniesBatchSql(companies)

        then:
        actual.split('\n').count { it.endsWith(';') } == 2
        !actual.contains(',\n\nINSERT')
    }

    def "should escape single quote in job offer title and description"() {
        given:
        def offer = new JobOffer(JOB_OFFER_ID_1, COMPANY_ID_1, "Engineer's Role", "O'Brien's team",
                BigDecimal.TEN, BigDecimal.TEN, 'PLN', 0, 100, JobOfferStatus.ACTIVE,
                [] as EmploymentType[])
        def jobOffers = [offer] as JobOffer[]

        when:
        def actual = generator.generateJobOffersBatchSql(jobOffers)

        then:
        actual.contains("Engineer''s Role")
        actual.contains("O''Brien''s team")
    }
}
