package pl.lunasoftware.demo.microservices.candidates.candidate.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.candidates.candidate.CandidateService
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOfferMatchDto
import pl.lunasoftware.demo.microservices.candidates.joboffer.JobOffersClient
import spock.lang.Specification

import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CandidateController)
class CandidateControllerSpec extends Specification {

    @Autowired
    private MockMvc mockMvc

    @MockitoBean
    private CandidateService candidateService

    @MockitoBean
    private JobOffersClient jobOffersClient

    def "GET matching-offers returns 200 with ranked list and formatted salaries"() {
        given:
        def candidateId = UUID.fromString('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11')
        def matches = [
                new JobOfferMatchDto(
                        UUID.fromString('b1ffcd00-9c0b-4ef8-bb6d-6bb9bd380b22'),
                        UUID.fromString('c2aacd00-9c0b-4ef8-bb6d-6bb9bd380c33'),
                        'TechCorp', 'Senior Java Dev', null,
                        new BigDecimal('18000.00'), new BigDecimal('24000.00'),
                        'PLN', ['B2B'] as Set, 'ACTIVE', 1.0
                ),
                new JobOfferMatchDto(
                        UUID.fromString('d3ffcd00-9c0b-4ef8-bb6d-6bb9bd380d44'),
                        UUID.fromString('e4aacd00-9c0b-4ef8-bb6d-6bb9bd380e55'),
                        'FinTech', 'Backend Dev', null,
                        new BigDecimal('16000.00'), new BigDecimal('22000.00'),
                        'PLN', ['B2B'] as Set, 'ACTIVE', 0.6
                )
        ]
        when(candidateService.findMatchingOffers(candidateId)).thenReturn(matches)

        expect:
        mockMvc.perform(get('/api/v1/candidates/{id}/matching-offers', candidateId))
                .andExpect(status().isOk())
                .andExpect(content().json('''
                    [
                      {"id":"b1ffcd00-9c0b-4ef8-bb6d-6bb9bd380b22","companyId":"c2aacd00-9c0b-4ef8-bb6d-6bb9bd380c33","companyName":"TechCorp","title":"Senior Java Dev","description":null,"salaryFrom":"18000.00","salaryTo":"24000.00","currency":"PLN","employmentTypes":["B2B"],"status":"ACTIVE","score":1.0},
                      {"id":"d3ffcd00-9c0b-4ef8-bb6d-6bb9bd380d44","companyId":"e4aacd00-9c0b-4ef8-bb6d-6bb9bd380e55","companyName":"FinTech","title":"Backend Dev","description":null,"salaryFrom":"16000.00","salaryTo":"22000.00","currency":"PLN","employmentTypes":["B2B"],"status":"ACTIVE","score":0.6}
                    ]
                '''))
    }

    def "GET matching-offers returns 404 when candidate not found"() {
        given:
        def candidateId = UUID.fromString('00000000-0000-0000-0000-000000000000')
        when(candidateService.findMatchingOffers(candidateId))
                .thenThrow(new ResourceNotFoundException("Candidate not found: $candidateId"))

        expect:
        mockMvc.perform(get('/api/v1/candidates/{id}/matching-offers', candidateId))
                .andExpect(status().isNotFound())
                .andExpect(content().json('{"message":"Candidate not found: 00000000-0000-0000-0000-000000000000"}'))
    }

    def "GET matching-offers returns 400 when feign client fails"() {
        given:
        def candidateId = UUID.fromString('00000000-0000-0000-0000-000000000001')
        when(candidateService.findMatchingOffers(candidateId))
                .thenThrow(new BadRequestException('Job offers service returned bad request'))

        expect:
        mockMvc.perform(get('/api/v1/candidates/{id}/matching-offers', candidateId))
                .andExpect(status().isBadRequest())
                .andExpect(content().json('{"message":"Job offers service returned bad request"}'))
    }
}
