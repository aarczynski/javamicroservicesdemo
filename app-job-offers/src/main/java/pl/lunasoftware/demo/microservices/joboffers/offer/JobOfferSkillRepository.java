package pl.lunasoftware.demo.microservices.joboffers.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobOfferSkillRepository extends JpaRepository<JobOfferSkillEntity, UUID> {

    List<JobOfferSkillEntity> findByJobOfferId(UUID jobOfferId);

    List<JobOfferSkillEntity> findBySkillId(UUID skillId);
}
