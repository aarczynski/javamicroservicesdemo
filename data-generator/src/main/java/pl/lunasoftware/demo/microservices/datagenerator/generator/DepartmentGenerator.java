package pl.lunasoftware.demo.microservices.datagenerator.generator;

import com.github.javafaker.Faker;
import pl.lunasoftware.demo.microservices.datagenerator.generator.Department;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class DepartmentGenerator {

    public static final int MAX_DEPARTMENTS = 10_000;

    private final Faker faker = new Faker();

    public Set<Department> randomDepartments(int count) {
        count = validateDepartmentsCount(count);

        Set<Department> departments = new HashSet<>();
        while (departments.size() < count) {
            departments.addAll(
                    Stream.generate(this::randomDepartment)
                            .limit(count)
                            .collect(toSet())
            );
        }

        return departments.stream().limit(count).collect(toSet());
    }

    private Department randomDepartment() {
        return new Department(
                UUID.randomUUID(),
                String.join(" ", faker.company().name())
        );
    }

    private int validateDepartmentsCount(int count) {
        if (count > MAX_DEPARTMENTS) {
            System.out.println("Exceeded maximum departments count. Falling back to 10 000");
            count = MAX_DEPARTMENTS;
        }
        return count;
    }
}
