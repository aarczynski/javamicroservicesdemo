package pl.lunasoftware.demo.microservices.company.employee;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID> {

    @EntityGraph(value = "employee-departments")
    Optional<EmployeeEntity> findEmployeeByEmail(String email);
}
