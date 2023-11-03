package pl.lunasoftware.demo.microservices.datagenerator.model;

import com.github.javafaker.Faker;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class DepartmentGenerator {

    private final Faker faker = new Faker();

    public List<Department> randomDepartments(int count) {
        return Stream.generate(this::randomDepartment)
                .limit(count)
                .toList();
    }

    private Department randomDepartment() {
        return new Department(
                UUID.randomUUID(),
                faker.company().name()
        );
    }
}
