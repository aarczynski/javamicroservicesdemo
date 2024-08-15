package pl.lunasoftware.demo.microservices.company.employee;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EmployeeDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        BigDecimal salary,
        List<String> departments
) {
}
