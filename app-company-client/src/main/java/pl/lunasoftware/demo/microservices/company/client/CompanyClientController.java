package pl.lunasoftware.demo.microservices.company.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1")
public class CompanyClientController {

    private final static Logger LOG = LoggerFactory.getLogger(CompanyClientController.class);
    private final Executor executor = Executors.newFixedThreadPool(4);

    private final CompanyClient companyClient;
    private final RestTemplate restTemplate = new RestTemplate();

    public CompanyClientController(CompanyClient companyClient) {
        this.companyClient = companyClient;
    }

    @GetMapping("/employees/{email}")
    public EmployeeDto getEmployeeByEmail(@PathVariable String email) throws ExecutionException, InterruptedException {
        LOG.info("Received request for employee with email {}, forwarding to Company App", email);
//        EmployeeDto employee = CompletableFuture.supplyAsync(() -> companyClient.getEmployeeByEmail(email), executor).get();
        EmployeeDto employee = CompletableFuture.supplyAsync(
                () -> restTemplate.getForEntity("http://app-company:8080/api/v1/employees/" + email, EmployeeDto.class), executor
        ).get().getBody();
        LOG.info("Received response from Company App for employee with email {}", email);
        return employee;
    }

    @GetMapping("/departments/{departmentName}/costs")
    public DepartmentCostDto getDepartmentCost(@PathVariable String departmentName) {
        LOG.info("Received request for {} department cost, forwarding to Company App", departmentName);
        DepartmentCostDto departmentCost = companyClient.getDepartmentCost(departmentName);
        LOG.info("Received response from Company App: {} department cost is {}", departmentName, departmentCost.cost());
        return departmentCost;
    }

    @RestControllerAdvice
    static class CompanyClientControllerAdvice {

        @ResponseStatus(HttpStatus.NOT_FOUND)
        @ExceptionHandler(FeignException.NotFound.class)
        public ErrorResponse handleClientNotFound(FeignException.NotFound ex) {
            LOG.info("Downstream returned 404: {}", ex.getLocalizedMessage());
            return new ErrorResponse(ex.getLocalizedMessage());
        }

        record ErrorResponse(String info) { }
    }
}
