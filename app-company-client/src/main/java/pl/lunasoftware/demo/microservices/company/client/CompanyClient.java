package pl.lunasoftware.demo.microservices.company.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "CompanyAppClient")
public interface CompanyClient {

    @GetMapping("/api/v1/employees/{email}")
    EmployeeDto getEmployeeByEmail(@PathVariable String email);
}
