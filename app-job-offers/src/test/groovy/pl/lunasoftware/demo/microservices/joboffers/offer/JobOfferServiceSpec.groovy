package pl.lunasoftware.demo.microservices.joboffers.offer

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.joboffers.company.CompanyEntity
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSkillRequest
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillEntity
import spock.lang.Specification

import static org.instancio.Select.field

class JobOfferServiceSpec extends Specification {

    private static final double CANDIDATE_LAT = 52.2297
    private static final double CANDIDATE_LON = 21.0122
    private static final double RADIUS_KM = 100.0

    private JobOfferRepository jobOfferRepository = Mock()
    private JobOfferService service = new JobOfferService(jobOfferRepository)

    def "should return empty list when no offers match"() {
        given:
        def request = searchRequest(['Java': SeniorityLevel.MID], 5)
        jobOfferRepository.findCandidateMatches(*_) >> []

        when:
        def result = service.search(request)

        then:
        result.isEmpty()
    }

    def "should return matched offers sorted by score descending"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)

        def javaSkill = skill('Java')
        def springSkill = skill('Spring Boot')
        def reactSkill = skill('React')

        def offerWithBothSkills = offer(company, 20000, [
                offerSkill(javaSkill, '1.00', true, SeniorityLevel.MID),
                offerSkill(springSkill, '0.80', false, SeniorityLevel.MID)
        ])
        def offerWithOneSkill = offer(company, 20000, [
                offerSkill(javaSkill, '1.00', true, SeniorityLevel.MID),
                offerSkill(reactSkill, '0.80', false, SeniorityLevel.MID)
        ])
        jobOfferRepository.findCandidateMatches(*_) >> [offerWithOneSkill, offerWithBothSkills]

        when:
        def results = service.search(searchRequest(['Java': SeniorityLevel.MID, 'Spring Boot': SeniorityLevel.MID], 3))

        then:
        results.size() == 2
        results[0].id() == offerWithBothSkills.id
        results[1].id() == offerWithOneSkill.id
        results[0].score() > results[1].score()
    }

    def "should penalize score when candidate is missing a mandatory skill"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [
                offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID),
                offerSkill(skill('Spring Boot'), '0.80', true, SeniorityLevel.MID)
        ]
        def offerId = UUID.randomUUID()
        def theOffer = offer(company, 20000, offerSkills, offerId)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreWithBoth = service.search(searchRequest(['Java': SeniorityLevel.MID, 'Spring Boot': SeniorityLevel.MID], 3))[0].score()
        def scoreMissingSpring = service.search(searchRequest(['Java': SeniorityLevel.MID], 3))[0].score()

        then:
        scoreWithBoth > scoreMissingSpring
    }

    def "should reduce score when candidate seniority is below required"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.SENIOR)]
        def offerId = UUID.randomUUID()
        def theOffer = offer(company, 20000, offerSkills, offerId)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreAtLevel = service.search(searchRequest(['Java': SeniorityLevel.SENIOR], 5))[0].score()
        def scoreBelowLevel = service.search(searchRequest(['Java': SeniorityLevel.JUNIOR], 5))[0].score()

        then:
        scoreAtLevel > scoreBelowLevel
    }

    def "should reduce score when candidate expected salary is close to offer ceiling"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID)]
        def theOffer = offer(company, 20000, offerSkills)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreComfortableSalary = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('5000.00')))[0].score()
        def scoreAtCeiling = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('19000.00')))[0].score()

        then:
        scoreComfortableSalary > scoreAtCeiling
    }

    def "should reduce score when candidate lacks required years of experience"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.SENIOR)]
        def theOffer = offer(company, 20000, offerSkills, UUID.randomUUID(), 5)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreEnoughExp = service.search(searchRequest(['Java': SeniorityLevel.SENIOR], 6))[0].score()
        def scoreTooLittleExp = service.search(searchRequest(['Java': SeniorityLevel.SENIOR], 1))[0].score()

        then:
        scoreEnoughExp > scoreTooLittleExp
    }

    def "should reduce score when candidate remote preference mismatches office requirement"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID)]
        def offerId = UUID.randomUUID()
        def theOffer = offer(company, 20000, offerSkills, offerId, 0, 80)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreCompatible = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('10000.00'), 0))[0].score()
        def scoreMismatch = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('10000.00'), 60))[0].score()

        then:
        scoreCompatible > scoreMismatch
    }

    def "should give maximum distance score for fully remote offer regardless of candidate distance"() {
        given:
        def distantCompany = companyAt(51.5074, -0.1278) // London — ~1700 km, outside RADIUS_KM
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID)]
        def fullyRemoteOffer = offer(distantCompany, 20000, offerSkills, UUID.randomUUID(), 0, 0)
        def inOfficeOffer = offer(distantCompany, 20000, offerSkills, UUID.randomUUID(), 0, 100)
        jobOfferRepository.findCandidateMatches(*_) >> [fullyRemoteOffer, inOfficeOffer]

        when:
        def results = service.search(searchRequest(['Java': SeniorityLevel.MID], 3))

        then:
        results.find { it.id() == fullyRemoteOffer.id }.score() > results.find { it.id() == inOfficeOffer.id }.score()
    }

    def "should reduce distance penalty proportionally to allowed home office days"() {
        given:
        def distantCompany = companyAt(51.5074, -0.1278) // London — ~1700 km, outside RADIUS_KM
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID)]
        def fullyRemoteOffer = offer(distantCompany, 20000, offerSkills, UUID.randomUUID(), 0, 0)
        def hybridOffer = offer(distantCompany, 20000, offerSkills, UUID.randomUUID(), 0, 60)
        def inOfficeOffer = offer(distantCompany, 20000, offerSkills, UUID.randomUUID(), 0, 100)
        jobOfferRepository.findCandidateMatches(*_) >> [fullyRemoteOffer, hybridOffer, inOfficeOffer]

        when:
        def results = service.search(searchRequest(['Java': SeniorityLevel.MID], 3))

        then:
        results.find { it.id() == fullyRemoteOffer.id }.score() > results.find { it.id() == hybridOffer.id }.score()
        results.find { it.id() == hybridOffer.id }.score() > results.find { it.id() == inOfficeOffer.id }.score()
    }

    def "should give full remote score when candidate is willing to work more in office than required"() {
        given:
        def company = companyAt(CANDIDATE_LAT, CANDIDATE_LON)
        def offerSkills = [offerSkill(skill('Java'), '1.00', true, SeniorityLevel.MID)]
        def offerId = UUID.randomUUID()
        def theOffer = offer(company, 20000, offerSkills, offerId, 0, 40)
        jobOfferRepository.findCandidateMatches(*_) >> [theOffer]

        when:
        def scoreWillingMore = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('10000.00'), 20))[0].score()
        def scoreExactMatch = service.search(searchRequest(['Java': SeniorityLevel.MID], 3, new BigDecimal('10000.00'), 60))[0].score()

        then:
        scoreWillingMore == scoreExactMatch
    }

    // --- helpers ---

    private static CandidateSearchRequest searchRequest(Map<String, SeniorityLevel> skills, int yearsOfExperience,
                                                         BigDecimal expectedSalary = new BigDecimal('10000.00'),
                                                         int preferredRemoteDaysPercentage = 0) {
        def candidateSkills = skills.collect { name, level -> new CandidateSkillRequest(name, level) } as Set
        new CandidateSearchRequest(
                candidateSkills,
                CANDIDATE_LAT, CANDIDATE_LON, RADIUS_KM,
                expectedSalary,
                [EmploymentType.B2B] as Set,
                yearsOfExperience,
                preferredRemoteDaysPercentage
        )
    }

    private static CompanyEntity companyAt(double lat, double lon) {
        def company = new CompanyEntity()
        company.geoLat = lat
        company.geoLon = lon
        company
    }

    private static SkillEntity skill(String name) {
        def s = new SkillEntity()
        s.name = name
        s
    }

    private static JobOfferSkillEntity offerSkill(SkillEntity skill, String weight, boolean mandatory, SeniorityLevel level) {
        def jos = new JobOfferSkillEntity()
        jos.skill = skill
        jos.weight = new BigDecimal(weight)
        jos.mandatory = mandatory
        jos.requiredSeniorityLevel = level
        jos
    }

    private static JobOfferEntity offer(CompanyEntity company, double salaryTo,
                                        List<JobOfferSkillEntity> skills,
                                        UUID id = UUID.randomUUID(), int requiredYearsOfExperience = 0,
                                        int requiredOfficeDaysPercentage = 0) {
        Instancio.of(JobOfferEntity)
                .set(field(JobOfferEntity, 'id'), id)
                .set(field(JobOfferEntity, 'company'), company)
                .set(field(JobOfferEntity, 'salaryFrom'), new BigDecimal('10000.00'))
                .set(field(JobOfferEntity, 'salaryTo'), new BigDecimal(salaryTo))
                .set(field(JobOfferEntity, 'requiredYearsOfExperience'), requiredYearsOfExperience)
                .set(field(JobOfferEntity, 'requiredOfficeDaysPercentage'), requiredOfficeDaysPercentage)
                .set(field(JobOfferEntity, 'skills'), skills)
                .create()
    }
}
