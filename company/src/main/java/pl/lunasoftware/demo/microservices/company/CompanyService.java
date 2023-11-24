package pl.lunasoftware.demo.microservices.company;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pl.lunasoftware.demo.microservices.company.department.DepartmentCostDto;
import pl.lunasoftware.demo.microservices.company.department.DepartmentEntity;
import pl.lunasoftware.demo.microservices.company.department.DepartmentRepository;
import pl.lunasoftware.demo.microservices.company.department.DepartmentsCostDto;
import pl.lunasoftware.demo.microservices.company.employee.EmployeeDto;
import pl.lunasoftware.demo.microservices.company.employee.EmployeeRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompanyService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public CompanyService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public DepartmentsCostDto getAllDepartmentsCosts() {
        List<DepartmentCostDto> departmentsCosts = departmentRepository.findAllActiveEmployees()
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
        BigDecimal cost = departmentRepository.findAllActiveEmployeesForDepartment(departmentName)
                .map(DepartmentEntity::calculateTotalCost)
                .orElseThrow(() -> {
                    log.info("{} department not found", departmentName);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, departmentName + " not found");
                });

        log.info("{} department cost is {}", departmentName, cost);
        return new DepartmentCostDto(departmentName, cost);
    }

    public EmployeeDto findEmployee(String email) {
        EmployeeDto employeeDto = employeeRepository.findEmployeeByEmail(email)
                .map(e -> new EmployeeDto(
                        e.getId(),
                        e.getFirstName(),
                        e.getLastName(),
                        e.getEmail(),
                        e.getDepartments().stream()
                                .map(DepartmentEntity::getName)
                                .toList()
                ))
                .orElseThrow(() -> {
                    log.info("{} employee not found", email);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, email + " not found");
                });
        log.info("Found employee {}", email);
        return employeeDto;
    }
}
