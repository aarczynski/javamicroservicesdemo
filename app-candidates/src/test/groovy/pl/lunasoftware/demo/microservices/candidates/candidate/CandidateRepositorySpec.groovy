package pl.lunasoftware.demo.microservices.candidates.candidate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.transaction.TestTransaction
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel
import spock.lang.Specification

@DataJpaTest
class CandidateRepositorySpec extends Specification {

    @MockitoBean
    private JobOffersClient jobOffersClient

    @Autowired
    private CandidateRepository candidateRepository

    def "should find all candidates"() {
        when:
        def candidates = candidateRepository.findAll()

        then:
        candidates*.email as Set == ['jan.kowalski@example.com', 'anna.nowak@example.com'] as Set
    }

    def "should load skills and employment types when finding candidate by id"() {
        given:
        def jan = candidateRepository.findAll().find { it.email == 'jan.kowalski@example.com' }

        when:
        def candidate = candidateRepository.findWithSkillsAndEmploymentTypesById(jan.id).get()

        then:
        candidate.skills*.skillName as Set == ['Java', 'Spring Boot'] as Set
        candidate.skills.every { it.seniorityLevel == SeniorityLevel.SENIOR }
        candidate.preferredEmploymentTypes == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set
    }

    def "should not duplicate skills when fetching them together with employment types"() {
        given:
        def jan = candidateRepository.findAll().find { it.email == 'jan.kowalski@example.com' }

        when:
        def candidate = candidateRepository.findWithSkillsAndEmploymentTypesById(jan.id).get()

        then:
        candidate.skills.size() == 2
        candidate.preferredEmploymentTypes.size() == 2
    }

    def "should return collections usable after the persistence session ends"() {
        given:
        def janId = candidateRepository.findAll().find { it.email == 'jan.kowalski@example.com' }.id
        TestTransaction.flagForRollback()
        TestTransaction.end()
        TestTransaction.start()

        when:
        def candidate = candidateRepository.findWithSkillsAndEmploymentTypesById(janId).get()
        TestTransaction.end()

        then:
        noExceptionThrown()
        candidate.skills*.skillName as Set == ['Java', 'Spring Boot'] as Set
        candidate.preferredEmploymentTypes == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set

        cleanup:
        TestTransaction.start()
    }
}
