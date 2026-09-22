package pl.lunasoftware.demo.microservices.otelext;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * The agent's native runtime-telemetry-java8 instrumentation and the Micrometer bridge
 * (activated by OTEL_INSTRUMENTATION_SPRING_BOOT_ACTUATOR_AUTOCONFIGURE_ENABLED) both export
 * jvm.* metrics under the same names with different types/sources, which corrupts the export
 * and gets jvm_cpu_recent_utilization_ratio stuck at a flat 0
 * (opentelemetry-java-instrumentation#11122). The Micrometer bridge is otherwise needed for
 * hikaricp.* connection pool metrics, which have no native-instrumentation equivalent. Dropping
 * just the overlapping jvm.* metrics from the Micrometer scope keeps both: native
 * instrumentation stays the sole source of jvm.* metrics, the bridge stays the sole source of
 * everything else (hikaricp.*, etc).
 */
public class MicrometerJvmMetricFilterCustomizer implements AutoConfigurationCustomizerProvider {

    private static final String MICROMETER_SCOPE_NAME = "io.opentelemetry.micrometer-1.5";
    private static final String CLASHING_METRIC_PREFIX = "jvm.";

    @Override
    public void customize(AutoConfigurationCustomizer autoConfiguration) {
        autoConfiguration.addMetricExporterCustomizer(MicrometerJvmMetricFilterCustomizer::filterDuplicateJvmMetrics);
    }

    private static MetricExporter filterDuplicateJvmMetrics(MetricExporter delegate, ConfigProperties config) {
        return new MetricExporter() {
            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return delegate.getAggregationTemporality(instrumentType);
            }

            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                Collection<MetricData> filtered = metrics.stream()
                        .filter(metric -> !isMicrometerJvmMetric(metric))
                        .collect(Collectors.toList());
                return delegate.export(filtered);
            }

            @Override
            public CompletableResultCode flush() {
                return delegate.flush();
            }

            @Override
            public CompletableResultCode shutdown() {
                return delegate.shutdown();
            }

            @Override
            public String toString() {
                return "MicrometerJvmMetricFilterCustomizer{delegate=" + delegate + "}";
            }
        };
    }

    private static boolean isMicrometerJvmMetric(MetricData metric) {
        return MICROMETER_SCOPE_NAME.equals(metric.getInstrumentationScopeInfo().getName())
                && metric.getName().startsWith(CLASHING_METRIC_PREFIX);
    }
}
