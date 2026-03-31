package pl.lunasoftware.demo.microservices.datagenerator.generator.skill;

import java.util.Objects;
import java.util.UUID;

public record Skill(
        UUID id,
        String name
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Skill that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
