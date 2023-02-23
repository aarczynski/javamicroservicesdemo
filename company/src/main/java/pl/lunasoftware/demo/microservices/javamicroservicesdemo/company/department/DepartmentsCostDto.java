package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company.department;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.util.List;

public record DepartmentsCostDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
        List<DepartmentCostDto> departmentsCosts) {
}
