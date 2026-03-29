package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType;

import java.math.BigDecimal;
import java.util.Set;

public record CandidateSearchRequest(
        @NotEmpty Set<String> skillNames,
        double geoLat,
        double geoLon,
        @Positive double radiusKm,
        @NotNull @DecimalMin("0") BigDecimal expectedSalary,
        @NotEmpty Set<EmploymentType> preferredEmploymentTypes
) {}
