package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.EmployeeSqlDataReader;

import java.nio.file.Path;
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

public class EmployeeSimulation extends LoadSimulation {

    private static final int MAX_RPS = 100;
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
        employeeReader = new EmployeeSqlDataReader(Path.of("data-generator/output/employees.sql"));
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
                        .check(status().is(200))
                );
    }

    private Iterator<Map<String, Object>> employeeEmailFeeder() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> Collections.singletonMap("email", employeeReader.readRandomEmployeeEmail())
        ).iterator();
    }
}
