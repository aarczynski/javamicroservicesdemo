package pl.lunasoftware.demo.microservices.joboffers.observability

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply
import spock.lang.Specification
import spock.lang.Unroll

class MicrometerJvmMetricFilterConfigSpec extends Specification {

    private MeterFilter filter = new MicrometerJvmMetricFilterConfig().denyNativeInstrumentationClashingMeters()

    @Unroll
    def "should deny '#meterName' which clashes with native OTel instrumentation"() {
        expect:
        filter.accept(idFor(meterName)) == MeterFilterReply.DENY

        where:
        meterName << ['process.cpu.usage', 'jvm.memory.used', 'jvm.gc.pause', 'jvm.threads.live']
    }

    @Unroll
    def "should not deny '#meterName' which does not clash"() {
        expect:
        filter.accept(idFor(meterName)) != MeterFilterReply.DENY

        where:
        meterName << ['hikaricp.connections.active', 'system.cpu.usage', 'http.server.requests']
    }

    private static Meter.Id idFor(String name) {
        new Meter.Id(name, Tags.empty(), null, null, Meter.Type.GAUGE)
    }
}
