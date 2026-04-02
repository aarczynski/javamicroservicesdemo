package pl.lunasoftware.demo.microservices.candidates.candidate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillRepository
import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel
import spock.lang.Specification

@DataJpaTest
class CandidateSkillRepositorySpec extends Specification {

    @MockitoBean
    JobOffersClient jobOffersClient

    @Autowired
    private CandidateSkillRepository candidateSkillRepository

    @Autowired
    private CandidateRepository candidateRepository

    def "should find skills for candidate"() {
        given:
        def candidate = candidateRepository.findByEmail('jan.kowalski@example.com').get()

        when:
        def skills = candidateSkillRepository.findByCandidateId(candidate.id)

        then:
        skills*.skillName as Set == ['Java', 'Spring Boot'] as Set
    }

    def "should find skills with seniority level"() {
        given:
        def candidate = candidateRepository.findByEmail('jan.kowalski@example.com').get()

        when:
        def skills = candidateSkillRepository.findByCandidateId(candidate.id)

        then:
        skills.every { it.seniorityLevel == SeniorityLevel.SENIOR }
    }

    def "should delete all skills for candidate"() {
        given:
        def candidate = candidateRepository.findByEmail('jan.kowalski@example.com').get()

        when:
        candidateSkillRepository.deleteAllByCandidateId(candidate.id)
        def skills = candidateSkillRepository.findByCandidateId(candidate.id)

        then:
        skills.isEmpty()
    }

    def "should not affect other candidate's skills when deleting"() {
        given:
        def jan = candidateRepository.findByEmail('jan.kowalski@example.com').get()
        def anna = candidateRepository.findByEmail('anna.nowak@example.com').get()

        when:
        candidateSkillRepository.deleteAllByCandidateId(jan.id)

        then:
        candidateSkillRepository.findByCandidateId(anna.id).size() == 2
    }
}
