package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import pl.lunasoftware.demo.microservices.joboffers.offer.EmploymentType;
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferStatus;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record JobOfferMatchDto(
        UUID id,
        UUID companyId,
        String companyName,
        String title,
        String description,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String currency,
        Set<EmploymentType> employmentTypes,
        JobOfferStatus status,
        double score
) {}
