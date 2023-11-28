package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.DepartmentsSqlDataReader;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DepartmentSimulation extends Simulation {

    private static final int RPS = 100;
    private static final int LOAD_TEST_DURATION_SECS = 10;

    private final DepartmentsSqlDataReader departmentsReader = new DepartmentsSqlDataReader();

    public DepartmentSimulation() {
        this.setUp(departmentsCostsScenario()
                .injectOpen(constantUsersPerSec(RPS).during(Duration.ofSeconds(LOAD_TEST_DURATION_SECS)))
                .protocols(httpProtocolBuilder()));
    }

    private ScenarioBuilder departmentsCostsScenario() {
        return scenario("Load Test get departments costs")
                .feed(departmentNameFeeder())
                .exec(http("get departments costs")
                        .get("/departments/#{departmentName}/costs")
                        .header("Content-Type", "application/json")
                        .check(status().is(200))
                );
    }

    private HttpProtocolBuilder httpProtocolBuilder() {
        return http
                .baseUrl("http://localhost:8080")
                .acceptHeader("application/json")
                .userAgentHeader("Gatling/Performance Test");
    }

    private Iterator<Map<String, Object>> departmentNameFeeder() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> Collections.singletonMap("departmentName", departmentsReader.readRandomDepartmentName())
        ).iterator();
    }
}
