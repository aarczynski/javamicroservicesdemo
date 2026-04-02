package pl.lunasoftware.demo.microservices.joboffers.offer;

import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Observed(name = "job-offers.db")
@Repository
public interface JobOfferSkillRepository extends JpaRepository<JobOfferSkillEntity, UUID> {

    List<JobOfferSkillEntity> findByJobOfferId(UUID jobOfferId);


}
