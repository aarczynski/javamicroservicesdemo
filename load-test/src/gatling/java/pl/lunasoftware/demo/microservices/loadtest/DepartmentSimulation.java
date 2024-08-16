package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.DepartmentsSqlDataReader;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DepartmentSimulation extends Simulation {

    private static final int RPS = 1;
    private static final Duration A_MINUTE = Duration.ofSeconds(60);

    private DepartmentsSqlDataReader departmentsReader;

    public DepartmentSimulation() {
        this.setUp(departmentsCostsScenario()
                .injectOpen(
                        incrementUsersPerSec(RPS)
                                .times(4)
                                .eachLevelLasting(A_MINUTE)
                                .separatedByRampsLasting(A_MINUTE)
                                .startingFrom(RPS)
                )
                .protocols(httpProtocolBuilder()));
    }

    @Override
    public void before() {
        departmentsReader = new DepartmentsSqlDataReader(Path.of("data-generator/output/departments.sql"));
    }

    @Override
    public void after() {
        try {
            departmentsReader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ScenarioBuilder departmentsCostsScenario() {
        return scenario("Load Test get departments costs")
                .feed(departmentNameFeeder())
                .exec(http("get departments costs")
                        .get("/api/v1/departments/#{departmentName}/costs")
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
