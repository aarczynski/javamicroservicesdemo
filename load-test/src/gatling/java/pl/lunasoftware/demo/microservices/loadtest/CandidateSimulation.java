package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.CandidateSqlDataReader;

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

public class CandidateSimulation extends LoadSimulation {

    private static final int RPS = 10;
    private static final Duration A_MINUTE = Duration.ofSeconds(60);

    private CandidateSqlDataReader candidateReader;

    public CandidateSimulation() {
        this.setUp(candidateMatchingOffersScenario()
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
        candidateReader = new CandidateSqlDataReader(Path.of("data-generator/output/candidates/01-candidates.sql"));
    }

    @Override
    public void after() {
        try {
            candidateReader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ScenarioBuilder candidateMatchingOffersScenario() {
        return scenario("Load Test get candidate matching offers")
                .feed(candidateIdFeeder())
                .exec(http("get candidate matching offers")
                        .get("/api/v1/candidates/#{candidateId}/matching-offers")
                        .check(status().is(200))
                );
    }

    private Iterator<Map<String, Object>> candidateIdFeeder() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> Collections.singletonMap("candidateId", candidateReader.readRandomCandidateId())
        ).iterator();
    }
}
