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
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.lunasoftware.demo.microservices.joboffers.company.CompanyEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@NamedEntityGraph(name = "JobOffer.withEmploymentTypes",
        attributeNodes = @NamedAttributeNode("offeredEmploymentTypes"))
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

    @ElementCollection
    @CollectionTable(name = "job_offer_employment_type", joinColumns = @JoinColumn(name = "job_offer_id"))
    @Column(name = "employment_type")
    @Enumerated(EnumType.STRING)
    private Set<EmploymentType> offeredEmploymentTypes = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private CompanyEntity company;

    @Enumerated(EnumType.STRING)
    private JobOfferStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
