package pl.lunasoftware.demo.microservices.candidates.candidate

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
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

    def "should find candidate by email"() {
        when:
        def candidate = candidateRepository.findByEmail('jan.kowalski@example.com')

        then:
        candidate.isPresent()
        candidate.get().firstName == 'Jan'
        candidate.get().lastName == 'Kowalski'
    }

    def "should return empty when email not found"() {
        when:
        def candidate = candidateRepository.findByEmail('unknown@example.com')

        then:
        candidate.isEmpty()
    }

    def "should load preferred employment types eagerly"() {
        when:
        def candidate = candidateRepository.findByEmail('jan.kowalski@example.com').get()

        then:
        candidate.preferredEmploymentTypes == [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as Set
    }
}
