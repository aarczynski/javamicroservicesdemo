package pl.lunasoftware.demo.microservices.company.employee;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {

    @Observed(name = "company.employee.db")
    @EntityGraph(value = "employee-departments")
    Optional<EmployeeEntity> findEmployeeByEmail(String email);
}
