package pl.lunasoftware.demo.microservices.company.employee;

import io.micrometer.core.annotation.Timed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {

    @Timed(value = "db_employee", histogram = true, percentiles = {0.5, 0.75, 0.9, 0.95, 0.99})
    @EntityGraph(value = "employee-departments")
    Optional<EmployeeEntity> findEmployeeByEmail(String email);
}
