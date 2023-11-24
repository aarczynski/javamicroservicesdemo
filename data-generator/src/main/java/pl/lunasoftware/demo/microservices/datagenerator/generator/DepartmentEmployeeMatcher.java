package pl.lunasoftware.demo.microservices.datagenerator.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DepartmentEmployeeMatcher {

    private static final int MAX_EMPLOYEES_PER_DEPARTMENT = 6;

    public Map<Employee, Set<Department>> assignEmployeesToDepartments(Collection<Employee> employees, Collection<Department> departments) {
        return employees.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        v -> this.pickNRandomDepartments(departments, ThreadLocalRandom.current().nextInt(2, MAX_EMPLOYEES_PER_DEPARTMENT))
                ));
    }

    private Set<Department> pickNRandomDepartments(Collection<Department> departments, int n) {
        List<Department> departmentsCopy = new ArrayList<>(departments);
        Collections.shuffle(departmentsCopy);
        return new HashSet<>(departmentsCopy.subList(0, n + 1));
    }
}
