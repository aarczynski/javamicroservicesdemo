package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class DepartmentsSimulation extends Simulation {

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
        return CoreDsl.scenario("Load Test Creating Customers")
                .exec(http("get departments costs")
                        .get("/departments/it/costs")
                        .header("Content-Type", "application/json")
                        .check(status().is(200))
                );
    }
}
