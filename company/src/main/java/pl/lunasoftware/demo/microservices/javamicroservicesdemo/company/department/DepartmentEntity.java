package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department;

import lombok.Data;
import lombok.EqualsAndHashCode;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.employee.EmployeeEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DepartmentEntity {
    private UUID id;
    @EqualsAndHashCode.Include
    private String name;
    private Set<EmployeeEntity> employees = new HashSet<>();

    public BigDecimal calculateTotalCost() {
        return employees.stream()
                .map(EmployeeEntity::getSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
