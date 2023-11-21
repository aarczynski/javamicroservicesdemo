package pl.lunasoftware.demo.microservices.company;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.lunasoftware.demo.microservices.company.department.DepartmentCostDto;
import pl.lunasoftware.demo.microservices.company.department.DepartmentsCostDto;

@Slf4j
@RestController
public class CompanyController {

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    private final CompanyService companyService;

    @GetMapping("/departments/costs")
    public DepartmentsCostDto getTotalCost() {
        return companyService.getAllDepartmentsCosts();
    }

    @GetMapping("/departments/{departmentName}/costs")
    public DepartmentCostDto getDepartmentCost(@PathVariable String departmentName) {
        log.info("Received request for {} department cost", departmentName);
        return companyService.getDepartmentCost(departmentName.toLowerCase());
    }
}
