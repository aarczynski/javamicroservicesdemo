package pl.lunasoftware.demo.microservices.joboffers.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "JobOfferSkill")
@Table(name = "job_offer_skill")
public class JobOfferSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    private UUID jobOfferId;

    @EqualsAndHashCode.Include
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    private SeniorityLevel requiredSeniorityLevel;

    @Column(name = "mandatory")
    private boolean mandatory;

    private BigDecimal weight;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
