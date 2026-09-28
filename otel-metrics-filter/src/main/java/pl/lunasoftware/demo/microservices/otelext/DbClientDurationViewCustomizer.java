package pl.lunasoftware.demo.microservices.otelext;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.ExplicitBucketHistogramOptions;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;

import java.util.List;
import java.util.Set;

/**
 * DB query durations used to come from the collector's span_metrics connector, fed by 100% of JDBC spans.
 * With spans sampled in the app (see {@link LocalTailSamplingSpanExporter}) they come from the agent's own
 * db.client.operation.duration histogram instead (OTEL_SEMCONV_STABILITY_OPT_IN=database). Two things about
 * it don't fit as-is:
 * <ul>
 *     <li>its default semconv buckets (1, 5, 10, 50, 100 ms...) are too coarse for queries with a ~3 ms median
 *     and ~25 ms p99, so it gets the same buckets the connector had;</li>
 *     <li>it carries the sanitized SQL text as db.query.text - one time series per distinct {@code IN (?, ?, ...)}
 *     list length, unbounded under real traffic. db.query.summary ("select candidate_skill") names the query
 *     just as well with a fixed set of values, so only that and the database identity are kept.</li>
 * </ul>
 */
public class DbClientDurationViewCustomizer implements AutoConfigurationCustomizerProvider {

    private static final String DB_CLIENT_DURATION_METRIC = "db.client.operation.duration";
    private static final List<Double> BUCKET_BOUNDARIES_SECONDS = List.of(
            0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
    );
    private static final Set<String> KEPT_ATTRIBUTES = Set.of("db.system.name", "db.namespace", "db.query.summary");

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        autoConfiguration.addMeterProviderCustomizer((meterProvider, config) -> meterProvider.registerView(
                InstrumentSelector.builder().setName(DB_CLIENT_DURATION_METRIC).build(),
                View.builder()
                        .setAggregation(connectorCompatibleHistogram())
                        .setAttributeFilter(KEPT_ATTRIBUTES)
                        .build()
        ));
    }

    private static Aggregation connectorCompatibleHistogram() {
        return Aggregation.explicitBucketHistogram(ExplicitBucketHistogramOptions.builder()
                .setBucketBoundaries(BUCKET_BOUNDARIES_SECONDS)
                .build());
    }
}
