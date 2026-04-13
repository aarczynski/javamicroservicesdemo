package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType;

import java.math.BigDecimal;
import java.util.Set;

public record CandidateSearchRequest(
        @NotEmpty Set<CandidateSkillRequest> candidateSkills,
        double geoLat,
        double geoLon,
        @Positive double radiusKm,
        @NotNull @DecimalMin("0") BigDecimal expectedSalary,
        @NotEmpty Set<EmploymentType> preferredEmploymentTypes,
        @Min(0) int yearsOfExperience,
        @Min(0) @Max(100) int preferredRemoteDaysPercentage
) {}
