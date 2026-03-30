package pl.lunasoftware.demo.microservices.candidates.joboffer;

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
        Set<String> employmentTypes,
        String status,
        double score
) {}
