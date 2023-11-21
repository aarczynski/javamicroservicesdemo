package pl.lunasoftware.demo.microservices.datagenerator.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record Employee(
        UUID id,
        String firstName,
        String lastName,
        String email,
        BigDecimal salary,
        Status status
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Employee employee)) return false;
        return Objects.equals(email, employee.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    public enum Status {
        ACTIVE, INACTIVE
    }
}


