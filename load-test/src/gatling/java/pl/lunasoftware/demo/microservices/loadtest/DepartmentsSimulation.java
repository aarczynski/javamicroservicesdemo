package pl.lunasoftware.demo.microservices.loadtest;

import com.github.javafaker.Faker;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

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

public class DepartmentsSimulation extends Simulation {

    private static final com.github.javafaker.Faker faker = new Faker();

    public DepartmentsSimulation() {
        this.setUp(departmentsCostsScenario().injectOpen(constantUsersPerSec(1000).during(Duration.ofSeconds(10))))
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
                        .check(status().is(404))
                );
    }

    private static Iterator<Map<String, Object>> departmentNameFeeder() {
        return Stream.generate((Supplier<Map<String, Object>>) () -> Collections.singletonMap("departmentName", faker.company().name())
        ).iterator();
    }
}
