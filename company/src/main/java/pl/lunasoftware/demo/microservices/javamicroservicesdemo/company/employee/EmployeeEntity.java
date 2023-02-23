package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.employee;

import lombok.Data;
import lombok.EqualsAndHashCode;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmployeeEntity {
    private UUID id;
    private String firstName;
    private String lastName;
    @EqualsAndHashCode.Include
    private String email;
    private BigDecimal salary;
    private Set<DepartmentEntity> departments = new HashSet<>();
}
