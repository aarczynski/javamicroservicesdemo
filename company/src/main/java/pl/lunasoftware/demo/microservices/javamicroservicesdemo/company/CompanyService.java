package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company;

import org.springframework.stereotype.Service;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentCostDto;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentsCostDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CompanyService {

    public DepartmentsCostDto getAllDepartmentsCost() {
        return new DepartmentsCostDto(BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP), List.of(getDepartmentCost("IT")));
    }

    public DepartmentCostDto getDepartmentCost(String departmentName) {
        return new DepartmentCostDto(departmentName, BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP));
    }
}
