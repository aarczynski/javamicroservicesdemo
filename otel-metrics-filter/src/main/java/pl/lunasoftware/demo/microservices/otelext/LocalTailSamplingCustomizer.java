package pl.lunasoftware.demo.microservices.otelext;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.time.Duration;

/**
 * Plain head sampling (OTEL_TRACES_SAMPLER=traceidratio) can't keep errors and slow requests: it decides
 * before the request runs. So spans are still recorded for every request and the keep/drop decision is
 * made at export time instead - see {@link LocalTailSamplingSpanExporter}.
 */
public class LocalTailSamplingCustomizer implements AutoConfigurationCustomizerProvider {

    private static final String RATIO_PROPERTY = "otel.demo.traces.sampling.ratio";
    private static final String SLOW_THRESHOLD_PROPERTY = "otel.demo.traces.sampling.slow-threshold";
    private static final double DEFAULT_RATIO = 0.01;
    private static final Duration DEFAULT_SLOW_THRESHOLD = Duration.ofMillis(500);

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        autoConfiguration.addSpanExporterCustomizer(LocalTailSamplingCustomizer::sampleLocally);
    }

    private static SpanExporter sampleLocally(SpanExporter delegate, ConfigProperties config) {
        return new LocalTailSamplingSpanExporter(
                delegate,
                config.getDouble(RATIO_PROPERTY, DEFAULT_RATIO),
                config.getDuration(SLOW_THRESHOLD_PROPERTY, DEFAULT_SLOW_THRESHOLD),
                System::nanoTime
        );
    }
}
