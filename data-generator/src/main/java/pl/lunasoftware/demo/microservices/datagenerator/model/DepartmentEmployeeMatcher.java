package pl.lunasoftware.demo.microservices.datagenerator.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DepartmentEmployeeMatcher {

    static final double DEPARTMENT_EMPLOYMENT_RATIO = 0.3;

    private final Random random = new Random();
    
    public Map<Department, List<Employee>> assignEmployeesToDepartments(Collection<Department> departments, Collection<Employee> employees) {
        return departments.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        v -> employees.stream().filter(thresholdFilter()).toList()
                ));
    }

    private Predicate<Employee> thresholdFilter() {
        return e -> random.nextFloat() < DEPARTMENT_EMPLOYMENT_RATIO;
    }
}
