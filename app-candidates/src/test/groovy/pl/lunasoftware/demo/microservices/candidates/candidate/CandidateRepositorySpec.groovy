package pl.lunasoftware.demo.microservices.candidates.candidate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel
import spock.lang.Specification

@DataJpaTest
class CandidateRepositorySpec extends Specification {

    @MockitoBean
    JobOffersClient jobOffersClient

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
        def candidate = candidateRepository.findById(jan.id).get()

        then:
        candidate.skills*.skillName as Set == ['Java', 'Spring Boot'] as Set
        candidate.skills.every { it.seniorityLevel == SeniorityLevel.SENIOR }
        candidate.preferredEmploymentTypes == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set
    }
}
