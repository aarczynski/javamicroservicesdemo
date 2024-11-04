package pl.lunasoftware.demo.microservices.company.client;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "CompanyAppClient", path = "/api/v1")
public interface CompanyClient {

    @WithSpan
    @GetMapping("/employees/{email}")
    EmployeeDto getEmployeeByEmail(@PathVariable String email);

    @WithSpan
    @GetMapping("/departments/{departmentName}/costs")
    DepartmentCostDto getDepartmentCost(@PathVariable String departmentName);

}
