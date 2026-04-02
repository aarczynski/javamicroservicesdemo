package pl.lunasoftware.demo.microservices.datagenerator.generator.company;

import java.util.Objects;
import java.util.UUID;

public record Company(
        UUID id,
        String name,
        double geoLat,
        double geoLon
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Company that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
