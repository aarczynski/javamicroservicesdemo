package pl.lunasoftware.demo.microservices.candidates.skill;

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

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "CandidateSkill")
@Table(name = "candidate_skill")
public class CandidateSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    @Column(name = "candidate_id")
    private UUID candidateId;

    @EqualsAndHashCode.Include
    private String skillName;

    @Enumerated(EnumType.STRING)
    private SeniorityLevel seniorityLevel;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
