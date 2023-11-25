package pl.lunasoftware.demo.microservices.datagenerator.generator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class DepartmentEmployeeMatcher {

    static final int MIN_EMPLOYEES_PER_DEPARTMENT = 1;
    static final int MAX_EMPLOYEES_PER_DEPARTMENT = 5;

    public Map<Employee, Department[]> assignEmployeesToDepartments(Employee[] employees, Department[] departments) {
        Map<Employee, Department[]> result = new HashMap<>();
        for (Employee e : employees) {
            for (int i = 0; i < ThreadLocalRandom.current().nextInt(2, MAX_EMPLOYEES_PER_DEPARTMENT); i++) {
                int n = ThreadLocalRandom.current().nextInt(MIN_EMPLOYEES_PER_DEPARTMENT, MAX_EMPLOYEES_PER_DEPARTMENT + 1);
                result.put(e, pickNRandomDepartments(departments, n));
            }
        }
        return result;
    }

    private Department[] pickNRandomDepartments(Department[] departments, int n) {
        Set<Integer> randomIds = new HashSet<>();
        while (randomIds.size() < n) {
            randomIds.add(ThreadLocalRandom.current().nextInt(departments.length));
        }

        Department[] result = new Department[n];
        int i = 0;
        for (Integer idx : randomIds) {
            result[i] = departments[idx];
            i++;
        }

        return result;
    }
}
