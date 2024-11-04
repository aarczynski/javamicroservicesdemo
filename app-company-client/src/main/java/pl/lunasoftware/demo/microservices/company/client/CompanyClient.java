package pl.lunasoftware.demo.microservices.company.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "CompanyAppClient", path = "/api/v1")
public interface CompanyClient {

    @GetMapping("/employees/{email}")
    EmployeeDto getEmployeeByEmail(@PathVariable String email);

    @GetMapping("/departments/{departmentName}/costs")
    DepartmentCostDto getDepartmentCost(@PathVariable String departmentName);

}
