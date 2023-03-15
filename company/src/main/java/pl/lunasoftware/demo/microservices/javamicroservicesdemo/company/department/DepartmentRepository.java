package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
