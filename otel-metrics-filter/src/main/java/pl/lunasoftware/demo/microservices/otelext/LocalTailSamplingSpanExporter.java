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

/**
 * Keeps a trace if its trace id falls into {@code ratio}, if any of its spans carries an HTTP 4xx/5xx
 * status code, or if its local root span took at least {@code slowThreshold} - the same policies the
 * collector's tail_sampling processor used to apply, decided here so the other ~99% is never serialized
 * or sent. Spans are buffered per trace until the local root span (no parent, or a remote one) ends.
 * The decision is per service: a trace slow only in app-candidates keeps just app-candidates' spans.
 * The ratio part is derived from the trace id alone, so every service keeps the same traces for it.
 */
final class LocalTailSamplingSpanExporter implements SpanExporter {

    private static final AttributeKey<Long> HTTP_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    private static final Duration TRACE_STATE_TTL = Duration.ofSeconds(10);

    private final SpanExporter delegate;
    private final long ratioUpperBound;
    private final long slowThresholdNanos;
    private final LongSupplier nanoClock;
    private final Map<String, TraceState> traces = new LinkedHashMap<>();

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
                .flatMap(span -> accept(span, now).stream())
                .toList();
    }

    private List<SpanData> accept(SpanData span, long now) {
        TraceState trace = traces.computeIfAbsent(span.getTraceId(), traceId -> new TraceState(now));
        if (trace.isDecided()) {
            return trace.keep ? List.of(span) : List.of();
        }
        trace.pending.add(span);
        if (!isLocalRoot(span)) {
            return List.of();
        }
        return trace.decide(shouldKeep(trace.pending, span));
    }

    private void evictExpired(long now) {
        long expiredBefore = now - TRACE_STATE_TTL.toNanos();
        Iterator<TraceState> oldestFirst = traces.values().iterator();
        while (oldestFirst.hasNext() && oldestFirst.next().createdAtNanos < expiredBefore) {
            oldestFirst.remove();
        }
    }

    private boolean shouldKeep(List<SpanData> traceSpans, SpanData localRoot) {
        return isInRatio(localRoot.getTraceId())
                || traceSpans.stream().anyMatch(LocalTailSamplingSpanExporter::hasHttpErrorStatus)
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

    private static final class TraceState {

        private final long createdAtNanos;
        private final List<SpanData> pending = new ArrayList<>();
        private Boolean keep;

        private TraceState(long createdAtNanos) {
            this.createdAtNanos = createdAtNanos;
        }

        private boolean isDecided() {
            return keep != null;
        }

        private List<SpanData> decide(boolean keep) {
            this.keep = keep;
            List<SpanData> decided = keep ? List.copyOf(pending) : List.of();
            pending.clear();
            return decided;
        }
    }
}
