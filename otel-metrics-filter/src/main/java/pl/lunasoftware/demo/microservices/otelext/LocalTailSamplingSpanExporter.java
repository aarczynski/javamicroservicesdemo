package pl.lunasoftware.demo.microservices.otelext;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Keeps a trace if its trace id falls into {@code ratio}, if any of its spans carries an HTTP 4xx/5xx
 * status code, or if its local root span took at least {@code slowThreshold} - the same policies the
 * collector's tail_sampling processor used to apply, decided here so the other ~99% is never serialized
 * or sent. Spans are buffered per trace until the local root span (no parent, or a remote one) ends.
 * The decision is per service: a trace slow only in app-candidates keeps just app-candidates' spans.
 * The ratio part is derived from the trace id alone, so every service keeps the same traces for it.
 * <p>
 * The decision is not remembered once made (see CLAUDE.md, "Trace sampling"): a span ending after its local
 * root waits for a root that never comes and is evicted after {@link #PENDING_TRACE_TTL}.
 */
final class LocalTailSamplingSpanExporter implements SpanExporter {

    private static final AttributeKey<Long> HTTP_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final Duration PENDING_TRACE_TTL = Duration.ofSeconds(30);

    private final SpanExporter delegate;
    private final long ratioUpperBound;
    private final long slowThresholdNanos;
    private final LongSupplier nanoClock;
    private final Map<String, PendingTrace> pendingTraces = new LinkedHashMap<>();

    LocalTailSamplingSpanExporter(SpanExporter delegate, double ratio, Duration slowThreshold, LongSupplier nanoClock) {
        this.delegate = delegate;
        this.ratioUpperBound = (long) (ratio * Long.MAX_VALUE);
        this.slowThresholdNanos = slowThreshold.toNanos();
        this.nanoClock = nanoClock;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        List<SpanData> kept = selectKept(spans);
        if (kept.isEmpty()) {
            return CompletableResultCode.ofSuccess();
        }
        return delegate.export(kept);
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
        return "LocalTailSamplingSpanExporter{delegate=" + delegate + "}";
    }

    private synchronized List<SpanData> selectKept(Collection<SpanData> spans) {
        long now = nanoClock.getAsLong();
        evictExpired(now);
        return spans.stream()
                .flatMap(span -> accept(span, now))
                .toList();
    }

    private Stream<SpanData> accept(SpanData span, long now) {
        if (!isLocalRoot(span)) {
            pendingTraces.computeIfAbsent(span.getTraceId(), traceId -> new PendingTrace(now)).spans.add(span);
            return Stream.empty();
        }
        PendingTrace pending = pendingTraces.remove(span.getTraceId());
        List<SpanData> children = pending == null ? List.of() : pending.spans;
        if (!shouldKeep(children, span)) {
            return Stream.empty();
        }
        return Stream.concat(children.stream(), Stream.of(span));
    }

    private void evictExpired(long now) {
        long expiredBefore = now - PENDING_TRACE_TTL.toNanos();
        Iterator<PendingTrace> oldestFirst = pendingTraces.values().iterator();
        while (oldestFirst.hasNext() && oldestFirst.next().createdAtNanos < expiredBefore) {
            oldestFirst.remove();
        }
    }

    private boolean shouldKeep(List<SpanData> children, SpanData localRoot) {
        return isInRatio(localRoot.getTraceId())
                || hasHttpErrorStatus(localRoot)
                || children.stream().anyMatch(LocalTailSamplingSpanExporter::hasHttpErrorStatus)
                || isSlow(localRoot);
    }

    private boolean isInRatio(String traceId) {
        long randomPart = Long.parseUnsignedLong(traceId.substring(16), 16) & Long.MAX_VALUE;
        return randomPart < ratioUpperBound;
    }

    private boolean isSlow(SpanData span) {
        return span.getEndEpochNanos() - span.getStartEpochNanos() >= slowThresholdNanos;
    }

    private static boolean hasHttpErrorStatus(SpanData span) {
        Long statusCode = span.getAttributes().get(HTTP_STATUS_CODE);
        return statusCode != null && statusCode >= 400 && statusCode <= 599;
    }

    private static boolean isLocalRoot(SpanData span) {
        SpanContext parent = span.getParentSpanContext();
        return !parent.isValid() || parent.isRemote();
    }

    private static final class PendingTrace {

        private final long createdAtNanos;
        private final List<SpanData> spans = new ArrayList<>();

        private PendingTrace(long createdAtNanos) {
            this.createdAtNanos = createdAtNanos;
        }
    }
}
