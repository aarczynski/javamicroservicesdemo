package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record DepartmentCostDto(
        String departmentName,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cost) {
}
