package pl.lunasoftware.demo.microservices.datagenerator.generator;

import com.github.javafaker.Faker;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DepartmentGenerator {

    public static final int MAX_DEPARTMENTS = 10_000;

    private final Faker faker = new Faker();

    public Department[] randomDepartments(int count) {
        count = validateDepartmentsCount(count);

        Set<Department> departments = new HashSet<>();
        while (departments.size() < count) {
            departments.add(randomDepartment());
        }

        Department[] result = new Department[count];
        int i = 0;
        for (Department d : departments) {
            result[i] = d;
            i++;
        }

        System.out.println("Generated " + result.length + " departments");
        return result;
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
