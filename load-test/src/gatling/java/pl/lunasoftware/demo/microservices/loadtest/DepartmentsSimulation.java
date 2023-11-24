package pl.lunasoftware.demo.microservices.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DepartmentsSimulation extends Simulation {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int RPS = 100;
    private static final int LOAD_TEST_DURATION_SECS = 10;
    private static final String[] departmentNames = departmentNames();

    public DepartmentsSimulation() {
        this.setUp(departmentsCostsScenario()
                        .injectOpen(constantUsersPerSec(RPS)
                                .during(Duration.ofSeconds(LOAD_TEST_DURATION_SECS))))
                .protocols(httpProtocolBuilder());
    }

    private static HttpProtocolBuilder httpProtocolBuilder() {
        return http
                .baseUrl("http://localhost:8080")
                .acceptHeader("application/json")
                .userAgentHeader("Gatling/Performance Test");
    }

    private static ScenarioBuilder departmentsCostsScenario() {
        return scenario("Load Test Creating Customers")
                .feed(departmentNameFeeder())
                .exec(http("get departments costs")
                        .get("/departments/#{departmentName}/costs")
                        .header("Content-Type", "application/json")
                        .check(status().in(200, 404))
                );
    }

    private static Iterator<Map<String, Object>> departmentNameFeeder() {
        int idx = ThreadLocalRandom.current().nextInt(departmentNames.length);
        return Stream.generate((Supplier<Map<String, Object>>) () -> Collections.singletonMap("departmentName", departmentNames[idx])
        ).iterator();
    }

    private static String[] departmentNames() {
        try {
            Department[] departments = objectMapper.readValue(Path.of("data-generator/output/departments.json").toFile().getAbsoluteFile(), Department[].class);
            return Arrays.stream(departments)
                    .map(Department::name)
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: data-generator/output/departments.json. Did you generate test data first?", e);
        }
    }

    record Department(UUID id, String name) { }
}
