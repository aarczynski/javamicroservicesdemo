package pl.lunasoftware.demo.microservices.company.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompanyClientController {

    private final static Logger LOG = LoggerFactory.getLogger(CompanyClientController.class);

    private final CompanyClient companyClient;

    public CompanyClientController(CompanyClient companyClient) {
        this.companyClient = companyClient;
    }

    @GetMapping("/api/v1/employees/{email}")
    public EmployeeDto getEmployeeByEmail(@PathVariable("email") String email) {
        LOG.info("Received request for employee with email {}. Forwarding to Company App", email);
        EmployeeDto employee = companyClient.getEmployeeByEmail(email);
        LOG.info("Received response from company app for employee with email {}", email);
        return employee;
    }
}
