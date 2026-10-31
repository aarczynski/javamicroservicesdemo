package pl.lunasoftware.demo.microservices.otelext

import io.opentelemetry.sdk.trace.SdkTracerProvider
import spock.lang.Specification

class Level1TraceFlagsCustomizerSpec extends Specification {

    def "should start sampled root spans without the random trace id flag"() {
        given:
        def tracerProvider = SdkTracerProvider.builder()
                .setIdGenerator(new Level1TraceFlagsCustomizer.UnflaggedRandomIdGenerator())
                .build()

        when:
        def span = tracerProvider.get('test').spanBuilder('root').startSpan()

        then:
        span.spanContext.traceFlags.asHex() == '01'

        cleanup:
        span?.end()
        tracerProvider?.close()
    }
}
