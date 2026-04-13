package pl.lunasoftware.demo.microservices.candidates.joboffer;

import com.fasterxml.jackson.annotation.JsonFormat;

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
        Set<String> employmentTypes,
        String status,
        double score
) {}
