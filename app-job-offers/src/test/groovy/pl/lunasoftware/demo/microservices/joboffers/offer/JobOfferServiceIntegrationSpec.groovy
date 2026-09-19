package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.transaction.TestTransaction
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSearchRequest
import pl.lunasoftware.demo.microservices.joboffers.offer.api.CandidateSkillRequest
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel
import spock.lang.Specification

/**
 * Guards against DTOs that hold uninitialized Hibernate proxies: {@link JobOfferService#search}
 * runs in its own transaction that is over by the time Spring MVC serializes the response, so
 * any lazy association reachable from {@link pl.lunasoftware.demo.microservices.joboffers.offer.api.JobOfferMatchDto}
 * must already be a detached, plain collection.
 */
@DataJpaTest
@Import(JobOfferService)
class JobOfferServiceIntegrationSpec extends Specification {

    @Autowired
    private JobOfferService jobOfferService

    private static final double WARSAW_LAT = 52.2297
    private static final double WARSAW_LON = 21.0122

    def "should return matches whose fields survive JSON serialization after the persistence session ends"() {
        given:
        def request = new CandidateSearchRequest(
                [new CandidateSkillRequest('Java', SeniorityLevel.MID)] as Set,
                WARSAW_LAT, WARSAW_LON, 100.0,
                new BigDecimal('20000.00'),
                [EmploymentType.B2B] as Set,
                5, 0
        )

        when:
        def results = jobOfferService.search(request)
        TestTransaction.end()

        then:
        noExceptionThrown()
        !results.isEmpty()
        results.every { it.employmentTypes() != null && !it.employmentTypes().isEmpty() }

        cleanup:
        TestTransaction.start()
    }
}
