package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import pl.lunasoftware.demo.microservices.loadtest.reader.CandidateSqlDataReader;
import pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.readMaxRps;
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.readRamps;
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.readStepDuration;

public class CandidateSimulation extends Simulation {

    private static final int NOT_FOUND_RATE_PER_MILLE = 1;

    private final int maxRps = readMaxRps();
    private final int ramps = readRamps();
    private final Duration stepDuration = readStepDuration();

    private CandidateSqlDataReader candidateReader;

    public CandidateSimulation() {
        this.setUp(candidateMatchingOffersScenario()
                .injectOpen(buildInjectionSteps())
                .protocols(httpProtocolBuilder()));
    }

    @Override
    public void before() {
        candidateReader = new CandidateSqlDataReader(CliParamProvider.readDataFile());
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
                        .check(status().in(200, 404))
                );
    }

    private Iterator<Map<String, Object>> candidateIdFeeder() {
        return Stream.generate(
                (Supplier<Map<String, Object>>) () -> {
                    boolean triggerNotFound = ThreadLocalRandom.current().nextInt(1000) < NOT_FOUND_RATE_PER_MILLE;
                    String candidateId = triggerNotFound
                            ? UUID.randomUUID().toString()
                            : candidateReader.readRandomCandidateId();
                    return Collections.singletonMap("candidateId", candidateId);
                }
        ).iterator();
    }

    private OpenInjectionStep[] buildInjectionSteps() {
        return Stream.concat(
                buildRampUpSteps(),
                Stream.of(buildPeakSteady(), buildCooldownStep())
        ).toArray(OpenInjectionStep[]::new);
    }

    private Stream<OpenInjectionStep> buildRampUpSteps() {
        return IntStream.range(0, ramps)
                .boxed()
                .flatMap(i -> Stream.of(
                        rampUsersPerSec((double) maxRps * i).to((double) maxRps * (i + 1)).during(stepDuration).randomized(),
                        constantUsersPerSec((double) maxRps * (i + 1)).during(stepDuration.multipliedBy(2)).randomized()
                ));
    }

    private OpenInjectionStep buildPeakSteady() {
        return constantUsersPerSec((double) maxRps * ramps).during(stepDuration.multipliedBy(3)).randomized();
    }

    private OpenInjectionStep buildCooldownStep() {
        return rampUsersPerSec((double) maxRps * ramps).to(0).during(stepDuration.multipliedBy(5)).randomized();
    }

    private HttpProtocolBuilder httpProtocolBuilder() {
        String host = CliParamProvider.readHost();
        String targetHost = host == null ? "http://localhost:8080" : (host.startsWith("http") ? host : "http://" + host);
        return http
                .baseUrl(targetHost)
                .acceptHeader("application/json")
                .userAgentHeader("Gatling/Performance Test");
    }
}
