package pl.lunasoftware.demo.microservices.joboffers.offer

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillEntity
import spock.lang.Specification

import static org.instancio.Select.field

class JobOfferServiceSpec extends Specification {

    private JobOfferRepository jobOfferRepository = Mock()
    private JobOfferService service = new JobOfferService(jobOfferRepository)

    def "should return empty list when no offers match"() {
        given:
        def request = new CandidateSearchRequest(
                ['Cobol'] as Set,
                52.2297, 21.0122, 100,
                new BigDecimal('15000.00'),
                [EmploymentType.B2B] as Set
        )
        jobOfferRepository.findCandidateMatches(*_) >> []

        when:
        def result = service.search(request)

        then:
        result.isEmpty()
    }

    def "should return matched offers sorted by score descending"() {
        given:
        def offerId1 = UUID.randomUUID()
        def offerId2 = UUID.randomUUID()

        def javaSkill = new SkillEntity(); javaSkill.name = 'Java'
        def springSkill = new SkillEntity(); springSkill.name = 'Spring Boot'
        def reactSkill = new SkillEntity(); reactSkill.name = 'React'

        def skill1 = new JobOfferSkillEntity(); skill1.skill = javaSkill; skill1.weight = new BigDecimal('1.00'); skill1.mandatory = true
        def skill2 = new JobOfferSkillEntity(); skill2.skill = springSkill; skill2.weight = new BigDecimal('0.80'); skill2.mandatory = false
        def skill3 = new JobOfferSkillEntity(); skill3.skill = javaSkill; skill3.weight = new BigDecimal('1.00'); skill3.mandatory = true
        def skill4 = new JobOfferSkillEntity(); skill4.skill = reactSkill; skill4.weight = new BigDecimal('0.80'); skill4.mandatory = false

        def offer1 = Instancio.of(JobOfferEntity)
                .set(field(JobOfferEntity, 'id'), offerId1)
                .set(field(JobOfferEntity, 'skills'), [skill1, skill2])
                .create()
        def offer2 = Instancio.of(JobOfferEntity)
                .set(field(JobOfferEntity, 'id'), offerId2)
                .set(field(JobOfferEntity, 'skills'), [skill3, skill4])
                .create()

        def request = new CandidateSearchRequest(
                ['Java', 'Spring Boot'] as Set,
                52.2297, 21.0122, 100,
                new BigDecimal('15000.00'),
                [EmploymentType.B2B] as Set
        )
        jobOfferRepository.findCandidateMatches(_, _, _, _, _, _, _) >> [offer1, offer2]

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
        def offerId = UUID.randomUUID()
        def javaSkill = new SkillEntity(); javaSkill.name = 'Java'
        def jos = new JobOfferSkillEntity(); jos.skill = javaSkill; jos.weight = new BigDecimal('1.00'); jos.mandatory = true
        def offer = Instancio.of(JobOfferEntity)
                .set(field(JobOfferEntity, 'id'), offerId)
                .set(field(JobOfferEntity, 'skills'), [jos])
                .create()

        def request = new CandidateSearchRequest(
                ['Java'] as Set,
                52.2297, 21.0122, 100,
                new BigDecimal('15000.00'),
                [EmploymentType.B2B] as Set
        )
        jobOfferRepository.findCandidateMatches(*_) >> [offer]

        when:
        def results = service.search(request)

        then:
        results.size() == 1
        results[0].score() == 1.0
    }
}
