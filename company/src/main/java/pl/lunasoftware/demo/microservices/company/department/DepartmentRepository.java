package pl.lunasoftware.demo.microservices.company.department;

import io.micrometer.core.annotation.Timed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {

    @EntityGraph(value = "department-employees")
    Optional<DepartmentEntity> findByNameIgnoringCase(String departmentName);

    @EntityGraph(value = "department-employees")
    List<DepartmentEntity> findAllBy();

    @Query("SELECT d FROM Department d JOIN FETCH d.employees e WHERE e.status = 'ACTIVE'")
    List<DepartmentEntity> findAllActiveEmployees();

    @Timed(value = "db_department", histogram = true, percentiles = {0.5, 0.75, 0.9, 0.95, 0.99})
    @Query("SELECT d FROM Department d JOIN FETCH d.employees e WHERE e.status = 'ACTIVE' AND d.name = :departmentName")
    Optional<DepartmentEntity> findAllActiveEmployeesForDepartment(String departmentName);
}
