package pl.lunasoftware.demo.microservices.joboffers.offer.api;

import com.fasterxml.jackson.annotation.JsonFormat;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal salaryFrom,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal salaryTo,
        String currency,
        int requiredOfficeDaysPercentage,
        Set<EmploymentType> employmentTypes,
        JobOfferStatus status,
        double score
) {}
