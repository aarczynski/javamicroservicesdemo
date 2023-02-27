package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentCostDto;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentsCostDto;

@RestController
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/departments/costs")
    public DepartmentsCostDto getTotalCost() {
        return companyService.getAllDepartmentsCosts();
    }

    @GetMapping("/departments/{departmentName}/costs")
    public DepartmentCostDto getDepartmentCost(@PathVariable String departmentName) {
        return companyService.getDepartmentCost(departmentName.toLowerCase());
    }
}
