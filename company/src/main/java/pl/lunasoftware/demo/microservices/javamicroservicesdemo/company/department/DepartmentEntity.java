package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.employee.EmployeeEntity;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "Department")
@Table(name = "department")
@NamedEntityGraph(name = "department-employees", attributeNodes = @NamedAttributeNode(value = "employees"))
public class DepartmentEntity {

    @Id
    private UUID id;
    @EqualsAndHashCode.Include
    private String name;
    @ManyToMany(cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "department_employee", joinColumns = @JoinColumn(name = "department_id"), inverseJoinColumns = @JoinColumn(name = "employee_id"))
    private Set<EmployeeEntity> employees = new HashSet<>();

    public BigDecimal calculateTotalCost() {
        return employees.stream()
                .map(EmployeeEntity::getSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
