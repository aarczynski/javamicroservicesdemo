package pl.lunasoftware.demo.microservices.datagenerator.model;

import com.github.javafaker.Faker;
import pl.lunasoftware.demo.microservices.datagenerator.model.Employee.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class EmployeeGenerator {

    static final double ACTIVE_STATUS_PROBABILITY = 0.999;

    private final Faker faker = new Faker();

    public List<Employee> randomEmployees(int count) {
        return Stream.generate(this::randomEmployee)
                .limit(count)
                .toList();
    }

    private Employee randomEmployee() {
        var firstName = faker.name().firstName();
        var lastName = faker.name().lastName();
        var emailBeginning = String.join(".", firstName, lastName, UUID.randomUUID().toString()).toLowerCase().replaceAll("'", "");

        return new Employee(
                UUID.randomUUID(),
                firstName,
                lastName,
                faker.internet().emailAddress(emailBeginning),
                BigDecimal.valueOf(faker.number().randomDouble(2, 5_000, 30_000)).setScale(2, RoundingMode.HALF_UP),
                randomEmployeeStatus()
        );
    }

    private Status randomEmployeeStatus() {
        return faker.number().randomDouble(10, 0, 1) < ACTIVE_STATUS_PROBABILITY ? Status.ACTIVE : Status.INACTIVE;
    }
}
