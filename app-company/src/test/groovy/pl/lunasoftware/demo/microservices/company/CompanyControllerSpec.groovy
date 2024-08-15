package pl.lunasoftware.demo.microservices.company

import org.instancio.Instancio
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import pl.lunasoftware.demo.microservices.company.department.DepartmentCostDto
import pl.lunasoftware.demo.microservices.company.department.DepartmentsCostDto
import pl.lunasoftware.demo.microservices.company.employee.EmployeeDto
import spock.lang.Specification

import static org.instancio.Select.field
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(CompanyController)
class CompanyControllerSpec extends Specification {

    @Autowired
    private MockMvc mockMvc

    @SpringBean
    private CompanyService companyService = Mock()

    def "should return json response for all departments costs"() {
        given:
        def dto = Instancio.of(DepartmentsCostDto)
                .generate(field('departmentsCosts'), gen -> gen.collection().size(1))
                .create()
        companyService.getAllDepartmentsCosts() >> dto

        when:
        def response = mockMvc.perform(get('/api/v1/departments/costs'))

        then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.total').value(dto.total()))
                .andExpect(jsonPath('$.departmentsCosts.length()').value(1))
                .andExpect(jsonPath('$.departmentsCosts[0].departmentName').value(dto.departmentsCosts[0].departmentName))
                .andExpect(jsonPath('$.departmentsCosts[0].cost').value(dto.departmentsCosts[0].cost))
    }

    def "should return json response for specific department cost"() {
        given:
        DepartmentCostDto dto = Instancio.create(DepartmentCostDto)
        companyService.getDepartmentCost('it') >> dto

        when:
        def response = mockMvc.perform(get('/api/v1/departments/it/costs'))

        then:
        response.andExpect(status().isOk())
                .andExpect(jsonPath('$.departmentName').value(dto.departmentName))
                .andExpect(jsonPath('$.cost').value(dto.cost))
    }

    def "should return json response for user"() {
        given:
        def dto = Instancio.of(EmployeeDto)
                .generate(field('departments'), gen -> gen.collection().size(1))
                .create()
        companyService.findEmployee(dto.email) >> dto

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
}
