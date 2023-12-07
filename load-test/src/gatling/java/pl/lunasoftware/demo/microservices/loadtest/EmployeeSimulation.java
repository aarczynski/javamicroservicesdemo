package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.EmployeeSqlDataReader;

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

public class EmployeeSimulation extends Simulation {

    private static final int RPS = 10;
    private static final int LOAD_TEST_DURATION_SECS = 600;

    private final EmployeeSqlDataReader employeeReader = new EmployeeSqlDataReader();

    public EmployeeSimulation() {
        this.setUp(employeesDataScenario()
                .injectOpen(constantUsersPerSec(RPS).during(Duration.ofSeconds(LOAD_TEST_DURATION_SECS)))
                .protocols(httpProtocolBuilder()));
    }

    private ScenarioBuilder employeesDataScenario() {
        return scenario("Load Test get employees data")
                .feed(employeeEmailFeeder())
                .exec(http("get employees data")
                        .get("/api/v1/employees/#{email}")
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

    private Iterator<Map<String, Object>> employeeEmailFeeder() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> Collections.singletonMap("email", employeeReader.readRandomEmployeeEmail())
        ).iterator();
    }
}
