package pl.lunasoftware.demo.microservices.joboffers.offer.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferService
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferStatus
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.when
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(JobOfferController)
class JobOfferControllerSpec extends Specification {

    private static final String VALID_REQUEST = '''
        {
          "candidateSkills": [
            {"skillName": "Java", "seniorityLevel": "MID"},
            {"skillName": "Spring Boot", "seniorityLevel": "MID"}
          ],
          "geoLat": 52.2297,
          "geoLon": 21.0122,
          "radiusKm": 100,
          "expectedSalary": 15000,
          "preferredEmploymentTypes": ["B2B"],
          "yearsOfExperience": 5,
          "preferredRemoteDaysPercentage": 40
        }
    '''

    @Autowired
    private MockMvc mockMvc

    @MockitoBean
    private JobOfferService jobOfferService

    def "POST search returns 200 with ranked results and formatted salaries"() {
        given:
        def matches = [
                new JobOfferMatchDto(
                        UUID.fromString('a1ffcd00-9c0b-4ef8-bb6d-6bb9bd380a11'),
                        UUID.fromString('b2aacd00-9c0b-4ef8-bb6d-6bb9bd380b22'),
                        'TechCorp', 'Senior Java Dev', null,
                        new BigDecimal('18000.00'), new BigDecimal('24000.00'),
                        'PLN', 60, [EmploymentType.B2B] as Set, JobOfferStatus.ACTIVE, 1.0
                ),
                new JobOfferMatchDto(
                        UUID.fromString('c3ffcd00-9c0b-4ef8-bb6d-6bb9bd380c33'),
                        UUID.fromString('d4aacd00-9c0b-4ef8-bb6d-6bb9bd380d44'),
                        'FinTech', 'Backend Dev', null,
                        new BigDecimal('16000.00'), new BigDecimal('22000.00'),
                        'PLN', 40, [EmploymentType.B2B] as Set, JobOfferStatus.ACTIVE, 0.56
                )
        ]
        when(jobOfferService.search(any())).thenReturn(matches)

        expect:
        mockMvc.perform(post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(content().json('''
                    [
                      {"id":"a1ffcd00-9c0b-4ef8-bb6d-6bb9bd380a11","companyId":"b2aacd00-9c0b-4ef8-bb6d-6bb9bd380b22","companyName":"TechCorp","title":"Senior Java Dev","description":null,"salaryFrom":"18000.00","salaryTo":"24000.00","currency":"PLN","requiredOfficeDaysPercentage":60,"employmentTypes":["B2B"],"status":"ACTIVE","score":1.0},
                      {"id":"c3ffcd00-9c0b-4ef8-bb6d-6bb9bd380c33","companyId":"d4aacd00-9c0b-4ef8-bb6d-6bb9bd380d44","companyName":"FinTech","title":"Backend Dev","description":null,"salaryFrom":"16000.00","salaryTo":"22000.00","currency":"PLN","requiredOfficeDaysPercentage":40,"employmentTypes":["B2B"],"status":"ACTIVE","score":0.56}
                    ]
                '''))
    }

    def "POST search returns 400 when candidateSkills is empty"() {
        expect:
        mockMvc.perform(post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"candidateSkills":[],"geoLat":52.2297,"geoLon":21.0122,"radiusKm":100,"expectedSalary":15000,"preferredEmploymentTypes":["B2B"],"yearsOfExperience":5,"preferredRemoteDaysPercentage":0}'))
                .andExpect(status().isBadRequest())
                .andExpect(content().json('{"message":"candidateSkills: must not be empty"}'))
    }

    def "POST search returns 400 when radiusKm is not positive"() {
        expect:
        mockMvc.perform(post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"candidateSkills":[{"skillName":"Java","seniorityLevel":"MID"}],"geoLat":52.2297,"geoLon":21.0122,"radiusKm":-10,"expectedSalary":15000,"preferredEmploymentTypes":["B2B"],"yearsOfExperience":5,"preferredRemoteDaysPercentage":0}'))
                .andExpect(status().isBadRequest())
                .andExpect(content().json('{"message":"radiusKm: must be greater than 0"}'))
    }

    def "POST search returns 400 when preferredEmploymentTypes is empty"() {
        expect:
        mockMvc.perform(post('/api/v1/job-offers/search')
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"candidateSkills":[{"skillName":"Java","seniorityLevel":"MID"}],"geoLat":52.2297,"geoLon":21.0122,"radiusKm":50,"expectedSalary":15000,"preferredEmploymentTypes":[],"yearsOfExperience":5,"preferredRemoteDaysPercentage":0}'))
                .andExpect(status().isBadRequest())
                .andExpect(content().json('{"message":"preferredEmploymentTypes: must not be empty"}'))
    }
}
