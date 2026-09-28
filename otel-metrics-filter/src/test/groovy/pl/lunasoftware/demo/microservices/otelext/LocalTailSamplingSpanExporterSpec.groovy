package pl.lunasoftware.demo.microservices.otelext

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.common.AttributeKey.longKey

class LocalTailSamplingSpanExporterSpec extends Specification {

    private static final String SAMPLED_OUT_TRACE_ID = '0af7651916cd43dd' + 'ffffffffffffffff'
    private static final String IN_RATIO_TRACE_ID = '0af7651916cd43dd' + '0000000000000001'
    private static final String ROOT_SPAN_ID = 'b7ad6b7169203331'
    private static final String CHILD_SPAN_ID = 'c8be7c8270314442'
    private static final long FAST_NANOS = TimeUnit.MILLISECONDS.toNanos(20)
    private static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(500)

    private InMemorySpanExporter delegate = InMemorySpanExporter.create()
    private long nowNanos = 0
    private LocalTailSamplingSpanExporter exporter = new LocalTailSamplingSpanExporter(
            delegate, 0.01, Duration.ofMillis(500), { nowNanos })

    def "should drop fast successful trace outside the ratio"() {
        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200), root(SAMPLED_OUT_TRACE_ID, FAST_NANOS, 200)])

        then:
        delegate.finishedSpanItems.isEmpty()
    }

    def "should keep whole trace when its trace id falls into the ratio"() {
        when:
        exporter.export([child(IN_RATIO_TRACE_ID, 200), root(IN_RATIO_TRACE_ID, FAST_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [CHILD_SPAN_ID, ROOT_SPAN_ID]
    }

    def "should keep whole trace when any span has HTTP status #statusCode"() {
        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, statusCode), root(SAMPLED_OUT_TRACE_ID, FAST_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [CHILD_SPAN_ID, ROOT_SPAN_ID]

        where:
        statusCode << [400, 404, 500, 599]
    }

    def "should keep whole trace when local root took at least the slow threshold"() {
        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200), root(SAMPLED_OUT_TRACE_ID, SLOW_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [CHILD_SPAN_ID, ROOT_SPAN_ID]
    }

    def "should hold children exported before their local root until the root decides"() {
        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200)])

        then:
        delegate.finishedSpanItems.isEmpty()

        when:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, SLOW_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [CHILD_SPAN_ID, ROOT_SPAN_ID]
    }

    def "should treat a span with a remote parent as the local root"() {
        given:
        def remoteParent = SpanContext.createFromRemoteParent(
                SAMPLED_OUT_TRACE_ID, 'd9cf8d9381425553', TraceFlags.sampled, TraceState.default)
        def serverSpan = span(SAMPLED_OUT_TRACE_ID, ROOT_SPAN_ID, remoteParent, SLOW_NANOS, 200)

        when:
        exporter.export([serverSpan])

        then:
        delegate.finishedSpanItems*.spanId == [ROOT_SPAN_ID]
    }

    def "should not export a span ending after its kept local root"() {
        given:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, SLOW_NANOS, 200)])

        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [ROOT_SPAN_ID]
    }

    def "should decide each local root of a trace on its own"() {
        given:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, FAST_NANOS, 200)])

        when:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, SLOW_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.endEpochNanos == [SLOW_NANOS]
    }

    def "should keep children of a request still running long after they ended"() {
        given:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200)])
        nowNanos += TimeUnit.SECONDS.toNanos(20)

        when:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, SLOW_NANOS, 200)])

        then:
        delegate.finishedSpanItems*.spanId == [CHILD_SPAN_ID, ROOT_SPAN_ID]
    }

    def "should drop a span ending after its dropped local root"() {
        given:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, FAST_NANOS, 200)])

        when:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 200)])

        then:
        delegate.finishedSpanItems.isEmpty()
    }

    def "should forget buffered spans whose local root never arrived within the TTL"() {
        given:
        exporter.export([child(SAMPLED_OUT_TRACE_ID, 500)])
        nowNanos += TimeUnit.SECONDS.toNanos(31)

        when:
        exporter.export([root(SAMPLED_OUT_TRACE_ID, FAST_NANOS, 200)])

        then:
        delegate.finishedSpanItems.isEmpty()
    }

    private static SpanData root(String traceId, long durationNanos, long statusCode) {
        span(traceId, ROOT_SPAN_ID, SpanContext.invalid, durationNanos, statusCode)
    }

    private static SpanData child(String traceId, long statusCode) {
        def parent = SpanContext.create(traceId, ROOT_SPAN_ID, TraceFlags.sampled, TraceState.default)
        span(traceId, CHILD_SPAN_ID, parent, FAST_NANOS, statusCode)
    }

    private static SpanData span(String traceId, String spanId, SpanContext parent, long durationNanos, long statusCode) {
        TestSpanData.builder()
                .setName('GET /api/v1/candidates/{id}/matching-offers')
                .setKind(SpanKind.SERVER)
                .setSpanContext(SpanContext.create(traceId, spanId, TraceFlags.sampled, TraceState.default))
                .setParentSpanContext(parent)
                .setStartEpochNanos(0)
                .setEndEpochNanos(durationNanos)
                .setHasEnded(true)
                .setStatus(StatusData.unset())
                .setAttributes(Attributes.of(longKey('http.response.status_code'), statusCode))
                .setTotalRecordedEvents(0)
                .setTotalRecordedLinks(0)
                .setTotalAttributeCount(1)
                .build()
    }
}
