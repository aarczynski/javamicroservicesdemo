package pl.lunasoftware.demo.microservices.otelext;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.trace.IdGenerator;

/**
 * The SDK marks the trace ids it generates as random (W3C Trace Context Level 2), so outgoing traceparent headers
 * carry flags {@code 03} instead of {@code 01}. Ory Hydra v26.2.0 drops such a parent and starts a trace of its own,
 * so its spans never join the app's trace. The ids are still random; only the flag, which nothing here reads, is
 * left out.
 */
public class Level1TraceFlagsCustomizer implements AutoConfigurationCustomizerProvider {

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        autoConfiguration.addTracerProviderCustomizer((tracerProvider, config) ->
                tracerProvider.setIdGenerator(new UnflaggedRandomIdGenerator()));
    }

    static final class UnflaggedRandomIdGenerator implements IdGenerator {

        private final IdGenerator random = IdGenerator.random();

        @Override
        public String generateSpanId() {
            return random.generateSpanId();
        }

        @Override
        public String generateTraceId() {
            return random.generateTraceId();
        }

        @Override
        public boolean generatesRandomTraceIds() {
            return false;
        }
    }
}
