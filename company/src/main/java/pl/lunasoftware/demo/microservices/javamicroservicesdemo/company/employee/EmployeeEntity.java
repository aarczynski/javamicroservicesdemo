package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.employee;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department.DepartmentEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "Employee")
@Table(name = "employee")
public class EmployeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String firstName;
    private String lastName;
    @EqualsAndHashCode.Include
    private String email;
    private BigDecimal salary;
    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;
    @ManyToMany(mappedBy = "employees")
    private Set<DepartmentEntity> departments = new HashSet<>();
}
