package pl.lunasoftware.demo.microservices.joboffers.offer.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferStatus
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferService
import pl.lunasoftware.demo.microservices.joboffers.offer.api.JobOfferController
import pl.lunasoftware.demo.microservices.joboffers.offer.api.JobOfferMatchDto
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(JobOfferController)
class JobOfferControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @Autowired
    ObjectMapper objectMapper

    @MockitoBean
    JobOfferService jobOfferService

    def "POST /search returns 200 with ranked results"() {
        given:
        def matches = [
            new JobOfferMatchDto(UUID.randomUUID(), UUID.randomUUID(), 'TechCorp', 'Senior Java Dev',
                null, 18000.00G, 24000.00G, 'PLN', [EmploymentType.B2B] as Set, JobOfferStatus.ACTIVE, 1.0),
            new JobOfferMatchDto(UUID.randomUUID(), UUID.randomUUID(), 'FinTech', 'Backend Dev',
                null, 16000.00G, 22000.00G, 'PLN', [EmploymentType.B2B] as Set, JobOfferStatus.ACTIVE, 0.56)
        ]
        Mockito.when(jobOfferService.search(any())).thenReturn(matches)

        def request = [
            skillNames: ['Java', 'Spring Boot'],
            geoLat: 52.2297,
            geoLon: 21.0122,
            radiusKm: 100,
            expectedSalary: 15000,
            preferredEmploymentTypes: ['B2B']
        ]

        expect:
        mockMvc.perform(
            post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath('$.length()').value(2))
        .andExpect(jsonPath('$[0].score').value(1.0))
        .andExpect(jsonPath('$[1].score').value(0.56))
    }

    def "POST /search returns 400 when skillNames is empty"() {
        given:
        def request = [
            skillNames: [],
            geoLat: 52.2297,
            geoLon: 21.0122,
            radiusKm: 100,
            expectedSalary: 15000,
            preferredEmploymentTypes: ['B2B']
        ]

        expect:
        mockMvc.perform(
            post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath('$.message').exists())
    }

    def "POST /search returns 400 when radiusKm is not positive"() {
        given:
        def request = [
            skillNames: ['Java'],
            geoLat: 52.2297,
            geoLon: 21.0122,
            radiusKm: -10,
            expectedSalary: 15000,
            preferredEmploymentTypes: ['B2B']
        ]

        expect:
        mockMvc.perform(
            post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath('$.message').exists())
    }

    def "POST /search returns 400 when preferredEmploymentTypes is empty"() {
        given:
        def request = [
            skillNames: ['Java'],
            geoLat: 52.2297,
            geoLon: 21.0122,
            radiusKm: 50,
            expectedSalary: 15000,
            preferredEmploymentTypes: []
        ]

        expect:
        mockMvc.perform(
            post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath('$.message').exists())
    }
}
