package pl.lunasoftware.demo.microservices.datagenerator.generator.offer;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record JobOffer(
        UUID id,
        UUID companyId,
        String title,
        String description,
        BigDecimal salaryFrom,
        BigDecimal salaryTo,
        String currency,
        JobOfferStatus status,
        EmploymentType[] employmentTypes
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JobOffer that)) return false;
        return Objects.equals(companyId, that.companyId) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, title);
    }
}
