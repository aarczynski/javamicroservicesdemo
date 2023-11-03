package pl.lunasoftware.demo.microservices.datagenerator.model;

import java.util.UUID;

public record Department(
        UUID id,
        String name
) {
}
