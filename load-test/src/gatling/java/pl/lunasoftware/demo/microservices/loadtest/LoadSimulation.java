package pl.lunasoftware.demo.microservices.loadtest;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.http.HttpDsl.http;
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.CLI_PARAM_PROVIDER;

public class LoadSimulation extends Simulation {
    protected HttpProtocolBuilder httpProtocolBuilder() {
        String targetHost = CLI_PARAM_PROVIDER.readHost() == null
                ? "http://localhost:8080"
                : checkScheme(CLI_PARAM_PROVIDER.readHost());
        return http
                .baseUrl(targetHost)
                .acceptHeader("application/json")
                .userAgentHeader("Gatling/Performance Test");
    }

    private String checkScheme(String url) {
        return url.startsWith("http") ? url : "http://" + url;
    }
}
