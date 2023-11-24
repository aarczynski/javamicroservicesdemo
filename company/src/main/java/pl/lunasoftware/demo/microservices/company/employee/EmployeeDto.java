package pl.lunasoftware.demo.microservices.company.employee;

import java.util.List;
import java.util.UUID;

public record EmployeeDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        List<String> departments
) {
}
