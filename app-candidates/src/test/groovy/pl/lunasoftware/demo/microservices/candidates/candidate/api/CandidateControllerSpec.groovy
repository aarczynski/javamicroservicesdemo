package pl.lunasoftware.demo.microservices.candidates.candidate.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.candidates.candidate.CandidateService
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(CandidateController)
class CandidateControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @MockitoBean
    CandidateService candidateService

    @MockitoBean
    JobOffersClient jobOffersClient

    def "GET /candidates/{id}/matching-offers returns 200 with ranked list"() {
        given:
        def id = UUID.randomUUID()
        def matches = [
            new JobOfferMatchDto(UUID.randomUUID(), UUID.randomUUID(), 'TechCorp', 'Senior Java Dev',
                null, 18000.00G, 24000.00G, 'PLN', ['B2B'] as Set, 'ACTIVE', 1.0),
            new JobOfferMatchDto(UUID.randomUUID(), UUID.randomUUID(), 'FinTech', 'Backend Dev',
                null, 16000.00G, 22000.00G, 'PLN', ['B2B'] as Set, 'ACTIVE', 0.6)
        ]
        Mockito.when(candidateService.findMatchingOffers(id)).thenReturn(matches)

        expect:
        mockMvc.perform(get("/api/v1/candidates/$id/matching-offers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath('$.length()').value(2))
        .andExpect(jsonPath('$[0].score').value(1.0))
        .andExpect(jsonPath('$[1].score').value(0.6))
    }

    def "GET /candidates/{id}/matching-offers returns 404 when candidate not found"() {
        given:
        def id = UUID.randomUUID()
        Mockito.when(candidateService.findMatchingOffers(id))
               .thenThrow(new ResourceNotFoundException("Candidate not found: $id" as String))

        expect:
        mockMvc.perform(get("/api/v1/candidates/$id/matching-offers"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath('$.message').value("Candidate not found: $id" as String))
    }

    def "GET /candidates/{id}/matching-offers returns 400 when feign client fails"() {
        given:
        def id = UUID.randomUUID()
        Mockito.when(candidateService.findMatchingOffers(id))
               .thenThrow(new BadRequestException("Job offers service returned bad request"))

        expect:
        mockMvc.perform(get("/api/v1/candidates/$id/matching-offers"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath('$.message').value('Job offers service returned bad request'))
    }
}
