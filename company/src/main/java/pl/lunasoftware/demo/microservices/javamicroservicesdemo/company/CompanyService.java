package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentCostDto;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentEntity;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentRepository;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentsCostDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final DepartmentRepository departmentRepository;

    public CompanyService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public DepartmentsCostDto getAllDepartmentsCosts() {
        List<DepartmentCostDto> departmentsCosts = departmentRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Function.identity(), DepartmentEntity::calculateTotalCost))
                .entrySet().stream()
                .map(e -> new DepartmentCostDto(e.getKey().getName(), e.getValue()))
                .toList();

        BigDecimal totalCost = departmentsCosts.stream()
                .map(DepartmentCostDto::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DepartmentsCostDto(totalCost, departmentsCosts);
    }

    public DepartmentCostDto getDepartmentCost(String departmentName) {
        BigDecimal cost = departmentRepository.findByNameIgnoringCase(departmentName)
                .map(DepartmentEntity::calculateTotalCost)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, departmentName + " not found"));
        return new DepartmentCostDto(departmentName, cost);
    }
}
