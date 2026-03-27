package pl.lunasoftware.demo.microservices.joboffers.skill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<SkillEntity, UUID> {

    Optional<SkillEntity> findByNameIgnoreCase(String name);

    List<SkillEntity> findByNameIn(Collection<String> names);
}
