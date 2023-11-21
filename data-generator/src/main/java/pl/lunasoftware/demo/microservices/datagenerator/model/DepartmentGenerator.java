package pl.lunasoftware.demo.microservices.datagenerator.model;

import com.github.javafaker.Faker;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DepartmentGenerator {

    private final Faker faker = new Faker();

    public Set<Department> randomDepartments(int count) {
        return Stream.generate(this::randomDepartment)
                .limit(count)
                .collect(Collectors.toSet());
    }

    private Department randomDepartment() {
        return new Department(
                UUID.randomUUID(),
                String.join(" ", faker.company().name())
        );
    }
}
