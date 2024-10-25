package pl.lunasoftware.demo.microservices.company.client

import feign.FeignException
import org.instancio.Instancio
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.instancio.Select.field
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CompanyClientController)
class CompanyClientControllerSpec extends Specification {

    @Autowired
    private MockMvc mockMvc

    @SpringBean
    private CompanyClient companyClient = Mock()

    def "should return json response for specific department cost"() {
        given:
        DepartmentCostDto dto = Instancio.create(DepartmentCostDto)
        companyClient.getDepartmentCost('it') >> dto

        when:
        def response = mockMvc.perform(get('/api/v1/departments/it/costs'))

        then:
        response.andExpect(status().isOk())
                .andExpect(content().json(
                        """\
                        {
                            "departmentName": "$dto.departmentName",
                            "cost": "$dto.cost"
                        }
                        """.stripIndent()
                ))
    }

    def "should return 404 response when department does not exist"() {
        given:
        companyClient.getDepartmentCost('dep') >> { throw mockFeignNotFoundException('dep not found') }

        when:
        def response = mockMvc.perform(get('/api/v1/departments/dep/costs'))

        then:
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath('$.info').value('dep not found'))
    }

    def "should return json response for user"() {
        given:
        def dto = Instancio.of(EmployeeDto)
                .generate(field('departments'), gen -> gen.collection().size(1))
                .create()
        companyClient.getEmployeeByEmail(dto.email) >> dto

        when:
        def response = mockMvc.perform(get("/api/v1/employees/$dto.email"))

        then:
        response.andExpect(status().isOk())
                .andExpect(content().json(
                        """\
                        {
                            "id": "$dto.id",
                            "firstName": "$dto.firstName",
                            "lastName": "$dto.lastName",
                            "email": "$dto.email",
                            "salary": $dto.salary,
                            "departments": [
                                "${dto.departments[0]}"
                            ]
                        }
                        """.stripIndent()
                ))
    }

    def "should return 404 response when employee does not exist"() {
        given:
        companyClient.getEmployeeByEmail('empl') >> { throw mockFeignNotFoundException('empl not found') }

        when:
        def response = mockMvc.perform(get('/api/v1/employees/empl'))

        then:
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath('$.info').value("empl not found"))
    }

    private FeignException.NotFound mockFeignNotFoundException(String message) {
        FeignException.NotFound feign404Exception = Mock(FeignException.NotFound)
        feign404Exception.getLocalizedMessage() >> message
        feign404Exception.status() >> 404
        return feign404Exception
    }
}
