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
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class EmployeeSimulation extends Simulation {

    private static final int MAX_RPS = 500;
    private static final Duration A_MINUTE = Duration.ofSeconds(60);

    private EmployeeSqlDataReader employeeReader;

    public EmployeeSimulation() {
        this.setUp(employeesDataScenario()
                .injectOpen(
                        rampUsersPerSec(0).to(MAX_RPS / 2.0).during(A_MINUTE).randomized(),
                        constantUsersPerSec(MAX_RPS / 2.0).during(A_MINUTE).randomized(),
                        rampUsersPerSec(MAX_RPS / 2.0).to(MAX_RPS).during(A_MINUTE).randomized(),
                        constantUsersPerSec(MAX_RPS).during(A_MINUTE).randomized(),
                        rampUsersPerSec(MAX_RPS).to(0).during(A_MINUTE).randomized()
                )
                .protocols(httpProtocolBuilder()));
    }

    @Override
    public void before() {
        employeeReader = new EmployeeSqlDataReader();
    }

    @Override
    public void after() {
        try {
            employeeReader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ScenarioBuilder employeesDataScenario() {
        return scenario("Load Test get employee data")
                .feed(employeeEmailFeeder())
                .exec(http("get employee data")
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
