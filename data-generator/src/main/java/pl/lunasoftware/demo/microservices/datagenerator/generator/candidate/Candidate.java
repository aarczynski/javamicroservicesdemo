package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate;

import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Candidate(
        UUID id,
        String firstName,
        String lastName,
        String email,
        double geoLat,
        double geoLon,
        double radiusKm,
        BigDecimal expectedSalary,
        EmploymentType[] preferredEmploymentTypes
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Candidate that)) return false;
        return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }
}
