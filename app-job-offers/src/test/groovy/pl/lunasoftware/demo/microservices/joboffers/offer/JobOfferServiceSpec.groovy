package pl.lunasoftware.demo.microservices.joboffers.offer

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferEntity
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferRepository
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferService
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferSkillEntity
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferSkillRepository
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillEntity
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillRepository
import spock.lang.Specification

import static org.instancio.Select.field

class JobOfferServiceSpec extends Specification {

    private JobOfferRepository jobOfferRepository = Mock()
    private JobOfferSkillRepository jobOfferSkillRepository = Mock()
    private SkillRepository skillRepository = Mock()
    private JobOfferService service = new JobOfferService(
            jobOfferRepository, jobOfferSkillRepository, skillRepository)

    def "should return empty list when no skills matched in DB"() {
        given:
        def request = new CandidateSearchRequest(
                ['Cobol'] as Set,
                52.2297, 21.0122, 100,
                15000.00 as BigDecimal,
                [EmploymentType.B2B] as Set
        )
        skillRepository.findByNameIn(_ as Collection) >> []

        when:
        def result = service.search(request)

        then:
        result.isEmpty()
        0 * jobOfferRepository.findCandidateMatches(*_)
    }

    def "should return matched offers sorted by score descending"() {
        given:
        def javaSkillId = UUID.randomUUID()
        def springSkillId = UUID.randomUUID()
        def reactSkillId = UUID.randomUUID()

        def javaSkill = Instancio.of(SkillEntity).set(field(SkillEntity, 'id'), javaSkillId).create()
        def springSkill = Instancio.of(SkillEntity).set(field(SkillEntity, 'id'), springSkillId).create()

        def offerId1 = UUID.randomUUID()
        def offerId2 = UUID.randomUUID()
        def offer1 = Instancio.of(JobOfferEntity).set(field(JobOfferEntity, 'id'), offerId1).create()
        def offer2 = Instancio.of(JobOfferEntity).set(field(JobOfferEntity, 'id'), offerId2).create()

        def request = new CandidateSearchRequest(
                ['Java', 'Spring Boot'] as Set,
                52.2297, 21.0122, 100,
                15000.00 as BigDecimal,
                [EmploymentType.B2B] as Set
        )

        skillRepository.findByNameIn(_ as Collection) >> [javaSkill, springSkill]
        jobOfferRepository.findCandidateMatches(*_) >> [offer1, offer2]

        // offer1: Java (1.0) + Spring Boot (0.8) → matched = 1.8 / 1.8 → score 1.0
        def skill1 = new JobOfferSkillEntity(jobOfferId: offerId1, skillId: javaSkillId, weight: 1.00G, mandatory: true)
        def skill2 = new JobOfferSkillEntity(jobOfferId: offerId1, skillId: springSkillId, weight: 0.80G, mandatory: false)
        // offer2: Java (1.0) + React (0.8, kandydat nie ma) → matched = 1.0 / 1.8 ≈ 0.556
        def skill3 = new JobOfferSkillEntity(jobOfferId: offerId2, skillId: javaSkillId, weight: 1.00G, mandatory: true)
        def skill4 = new JobOfferSkillEntity(jobOfferId: offerId2, skillId: reactSkillId, weight: 0.80G, mandatory: false)

        jobOfferSkillRepository.findByJobOfferId(offerId1) >> [skill1, skill2]
        jobOfferSkillRepository.findByJobOfferId(offerId2) >> [skill3, skill4]

        when:
        def results = service.search(request)

        then:
        results.size() == 2
        results[0].score() > results[1].score()
        results[0].id() == offerId1
        results[1].id() == offerId2
    }

    def "should return score 1.0 when candidate has all required skills"() {
        given:
        def skillId = UUID.randomUUID()
        def offerId = UUID.randomUUID()
        def skill = Instancio.of(SkillEntity).set(field(SkillEntity, 'id'), skillId).create()
        def offer = Instancio.of(JobOfferEntity).set(field(JobOfferEntity, 'id'), offerId).create()

        def request = new CandidateSearchRequest(
                ['Java'] as Set, 52.2297, 21.0122, 100,
                15000.00 as BigDecimal, [EmploymentType.B2B] as Set
        )

        skillRepository.findByNameIn(_ as Collection) >> [skill]
        jobOfferRepository.findCandidateMatches(*_) >> [offer]
        jobOfferSkillRepository.findByJobOfferId(offerId) >> [
            new JobOfferSkillEntity(jobOfferId: offerId, skillId: skillId, weight: 1.00G, mandatory: true)
        ]

        when:
        def results = service.search(request)

        then:
        results.size() == 1
        results[0].score() == 1.0
    }
}
