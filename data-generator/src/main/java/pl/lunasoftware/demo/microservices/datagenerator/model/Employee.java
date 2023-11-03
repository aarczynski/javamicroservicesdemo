package pl.lunasoftware.demo.microservices.datagenerator.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Employee(
        UUID id,
        String firstName,
        String lastName,
        String email,
        BigDecimal salary,
        Status status
) {
    public enum Status {
        ACTIVE, INACTIVE
    }
}


