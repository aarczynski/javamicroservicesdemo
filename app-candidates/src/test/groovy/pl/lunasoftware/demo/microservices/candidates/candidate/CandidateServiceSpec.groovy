package pl.lunasoftware.demo.microservices.candidates.candidate

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.candidates.candidate.api.ResourceNotFoundException
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillEntity
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillRepository
import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel
import spock.lang.Specification

import static org.instancio.Select.field

class CandidateServiceSpec extends Specification {

    private CandidateRepository candidateRepository = Mock()
    private CandidateSkillRepository candidateSkillRepository = Mock()
    private JobOffersClient jobOffersClient = Mock()
    private CandidateService service = new CandidateService(
            candidateRepository, candidateSkillRepository, jobOffersClient)

    def "should throw ResourceNotFoundException when candidate not found"() {
        given:
        def id = UUID.randomUUID()
        candidateRepository.findById(id) >> Optional.empty()

        when:
        service.findMatchingOffers(id)

        then:
        thrown(ResourceNotFoundException)
    }

    def "should return empty list when candidate has no skills"() {
        given:
        def id = UUID.randomUUID()
        def entity = Instancio.of(CandidateEntity).set(field(CandidateEntity, 'id'), id).create()
        candidateRepository.findById(id) >> Optional.of(entity)
        candidateSkillRepository.findByCandidateId(id) >> []

        when:
        def result = service.findMatchingOffers(id)

        then:
        result.isEmpty()
        0 * jobOffersClient.searchOffers(_)
    }

    def "should call job offers client and return matches"() {
        given:
        def id = UUID.randomUUID()
        def entity = Instancio.of(CandidateEntity)
                .set(field(CandidateEntity, 'id'), id)
                .set(field(CandidateEntity, 'preferredEmploymentTypes'), [EmploymentType.B2B] as Set)
                .create()
        def skill = new CandidateSkillEntity(candidateId: id, skillName: 'Java', seniorityLevel: SeniorityLevel.SENIOR)
        def match = Instancio.of(JobOfferMatchDto).create()

        candidateRepository.findById(id) >> Optional.of(entity)
        candidateSkillRepository.findByCandidateId(id) >> [skill]

        when:
        def result = service.findMatchingOffers(id)

        then:
        1 * jobOffersClient.searchOffers({ it.skillNames == ['Java'] as Set }) >> [match]
        result.size() == 1
    }
}
