package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company

import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentCostDto
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentsCostDto
import spock.lang.Specification

import java.math.RoundingMode

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@WebMvcTest(CompanyController)
class CompanyControllerSpec extends Specification {

    @Autowired
    private MockMvc mockMvc

    @SpringBean
    private CompanyService companyService = Mock()

    def "should return json response for all departments costs"() {
        given:
        companyService.getAllDepartmentsCosts() >> new DepartmentsCostDto(
                BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP), [buildTestDepartmentDto()])

        when:
        def response = mockMvc.perform(get('/departments/costs'))

        then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.total').value('10.00'))
                .andExpect(jsonPath('$.departmentsCosts[0].departmentName').value('IT'))
                .andExpect(jsonPath('$.departmentsCosts[0].cost').value('10.00'))
    }

    def "should return json response for specific department cost"() {
        DepartmentCostDto testDepartmentCostDto = buildTestDepartmentDto()
        given:
        companyService.getDepartmentCost('it') >> testDepartmentCostDto

        when:
        def response = mockMvc.perform(get('/departments/it/costs'))

        then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.departmentName').value('IT'))
                .andExpect(jsonPath('$.cost').value('10.00'))
    }

    def "department name URL param should be case insensitive"() {
        given:
        companyService.getDepartmentCost('it') >> buildTestDepartmentDto()

        when:
        def response = mockMvc.perform(get('/departments/IT/costs'))

        then:
        response.andExpect(status().isOk())
    }

    private DepartmentCostDto buildTestDepartmentDto() {
        def testDepartmentCostDto = new DepartmentCostDto("IT", BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP))
        testDepartmentCostDto
    }
}
