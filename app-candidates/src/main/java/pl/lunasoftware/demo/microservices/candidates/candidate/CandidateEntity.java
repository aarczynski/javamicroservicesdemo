package pl.lunasoftware.demo.microservices.candidates.candidate;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.lunasoftware.demo.microservices.candidates.skill.CandidateSkillEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NamedEntityGraph(name = "Candidate.withSkillsAndEmploymentTypes",
        attributeNodes = {
                @NamedAttributeNode("skills"),
                @NamedAttributeNode("preferredEmploymentTypes")
        })
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "Candidate")
@Table(name = "candidate")
public class CandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    private String email;

    private String firstName;
    private String lastName;
    private double geoLat;
    private double geoLon;
    private double radiusKm;
    private BigDecimal expectedSalary;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "candidate_preferred_employment_type", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "employment_type")
    @Enumerated(EnumType.STRING)
    private Set<EmploymentType> preferredEmploymentTypes = new HashSet<>();

    @OneToMany(mappedBy = "candidate", fetch = FetchType.LAZY)
    private List<CandidateSkillEntity> skills = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
