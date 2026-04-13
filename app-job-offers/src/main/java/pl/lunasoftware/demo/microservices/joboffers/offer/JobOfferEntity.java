package pl.lunasoftware.demo.microservices.joboffers.offer;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.NamedSubgraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.lunasoftware.demo.microservices.joboffers.company.CompanyEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NamedEntityGraph(name = "JobOffer.withAllRelations",
        attributeNodes = {
                @NamedAttributeNode("offeredEmploymentTypes"),
                @NamedAttributeNode("company"),
                @NamedAttributeNode(value = "skills", subgraph = "skills-subgraph")
        },
        subgraphs = @NamedSubgraph(
                name = "skills-subgraph",
                attributeNodes = @NamedAttributeNode("skill")
        ))
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity(name = "JobOffer")
@Table(name = "job_offer")
public class JobOfferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @EqualsAndHashCode.Include
    @Column(name = "company_id")
    private UUID companyId;

    @EqualsAndHashCode.Include
    private String title;

    private String description;
    private BigDecimal salaryFrom;
    private BigDecimal salaryTo;
    private String currency;

    @Column(name = "required_years_of_experience")
    private int requiredYearsOfExperience;

    @Column(name = "required_office_days_percentage")
    private int requiredOfficeDaysPercentage;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_offer_employment_type", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Column(name = "employment_type")
    @Enumerated(EnumType.STRING)
    private Set<EmploymentType> offeredEmploymentTypes = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private CompanyEntity company;

    @OneToMany(mappedBy = "jobOffer", fetch = FetchType.LAZY)
    private List<JobOfferSkillEntity> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private JobOfferStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
