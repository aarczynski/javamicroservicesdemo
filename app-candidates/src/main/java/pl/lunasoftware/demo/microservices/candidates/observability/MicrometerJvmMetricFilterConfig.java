package pl.lunasoftware.demo.microservices.candidates.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * otel-metrics-filter (the javaagent extension) drops the Micrometer bridge's jvm.* metrics at
 * export time, but that only prevents the corrupted export - it does not stop the Micrometer
 * bridge and the agent's native runtime-telemetry-java8 instrumentation from racing to register
 * the same OTel SDK instrument at JVM startup, which can still leave jvm_cpu_recent_utilization_ratio
 * stuck at 0 for a given instance (confirmed live, 2026-09-22: reproduced on 4 separate cluster
 * restarts despite the export-time filter). The clash traces back to Micrometer's own
 * "process.cpu.usage" meter (visible on /actuator/prometheus, NOT prefixed "jvm." at the
 * Micrometer level), which the OTel Micrometer bridge remaps to the semconv name
 * jvm.cpu.recent_utilization_ratio during export - the same name the native instrumentation
 * produces. Denying it here means Micrometer never creates the instrument in the first place,
 * which removes the race instead of filtering its output.
 */
@Configuration
public class MicrometerJvmMetricFilterConfig {

    private static final String PROCESS_CPU_USAGE_METRIC = "process.cpu.usage";
    private static final String JVM_METRIC_PREFIX = "jvm.";

    @Bean
    public MeterFilter denyNativeInstrumentationClashingMeters() {
        return MeterFilter.deny(this::clashesWithNativeInstrumentation);
    }

    private boolean clashesWithNativeInstrumentation(Meter.Id id) {
        return PROCESS_CPU_USAGE_METRIC.equals(id.getName()) || id.getName().startsWith(JVM_METRIC_PREFIX);
    }
}
