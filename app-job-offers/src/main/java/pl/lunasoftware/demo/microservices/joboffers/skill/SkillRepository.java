package pl.lunasoftware.demo.microservices.joboffers.skill;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Observed(name = "job-offers.db")
@Repository
public interface SkillRepository extends JpaRepository<SkillEntity, UUID> {

    Optional<SkillEntity> findByNameIgnoreCase(String name);

    List<SkillEntity> findByNameIn(Collection<String> names);
}
