package pl.lunasoftware.demo.microservices.joboffers.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Remembers the SSO's answer for an active token, so the SSO is asked once per token per TTL instead of on every
 * request (RFC 7662, section 4). A token revoked in the SSO is still accepted until its entry expires. Rejected tokens
 * are not cached — the delegate's exception propagates and the next request asks the SSO again. Requests arriving
 * with a token while the SSO is being asked about it wait for that one call instead of each making their own, so an
 * SSO that is down costs each of them one timeout, not a queue of them. Hits and misses are
 * exported as {@code cache.gets{cache="token-introspection"}}, and each lookup is a span with
 * {@code token.introspection.cache=hit|miss} — a miss has the call to the SSO under it. A miss also answers with
 * {@value #KEEP_TRACE_HEADER}, which tells the caller's trace sampling (otel-metrics-filter) to keep its part of the
 * trace: the caller took its token from its own cache, so without it the SSO call would have no caller above it.
 */
class CachingOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    static final String CACHE_NAME = "token-introspection";
    static final String KEEP_TRACE_HEADER = "X-Keep-Trace";
    private static final long MAX_CACHED_TOKENS = 10_000;
    private static final AttributeKey<String> CACHE_RESULT = AttributeKey.stringKey("token.introspection.cache");

    private final OpaqueTokenIntrospector delegate;
    private final AsyncCache<String, OAuth2AuthenticatedPrincipal> principals;

    CachingOpaqueTokenIntrospector(OpaqueTokenIntrospector delegate, Duration ttl, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.principals = Caffeine.newBuilder()
                .maximumSize(MAX_CACHED_TOKENS)
                .expireAfter(Expiry.creating((String token, OAuth2AuthenticatedPrincipal principal) -> cacheDuration(principal, ttl)))
                .recordStats()
                .buildAsync();
        CaffeineCacheMetrics.monitor(meterRegistry, principals, CACHE_NAME);
    }

    @Override
    @WithSpan
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        CompletableFuture<OAuth2AuthenticatedPrincipal> ssoCall = new CompletableFuture<>();
        CompletableFuture<OAuth2AuthenticatedPrincipal> principal = principals.get(token, (uncachedToken, executor) -> ssoCall);
        if (principal != ssoCall) {
            Span.current().setAttribute(CACHE_RESULT, "hit");
            return join(principal);
        }
        Span.current().setAttribute(CACHE_RESULT, "miss");
        askCallerToKeepTrace();
        return introspectInSso(token, ssoCall);
    }

    private static OAuth2AuthenticatedPrincipal join(CompletableFuture<OAuth2AuthenticatedPrincipal> principal) {
        try {
            return principal.join();
        } catch (CompletionException e) {
            throw unwrap(e);
        }
    }

    private static RuntimeException unwrap(CompletionException e) {
        if (e.getCause() instanceof RuntimeException cause) {
            return cause;
        }
        return e;
    }

    private static void askCallerToKeepTrace() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
                && request.getResponse() != null) {
            request.getResponse().setHeader(KEEP_TRACE_HEADER, "true");
        }
    }

    private OAuth2AuthenticatedPrincipal introspectInSso(String token, CompletableFuture<OAuth2AuthenticatedPrincipal> ssoCall) {
        try {
            OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
            ssoCall.complete(principal);
            return principal;
        } catch (RuntimeException e) {
            ssoCall.completeExceptionally(e);
            throw e;
        }
    }

    private static Duration cacheDuration(OAuth2AuthenticatedPrincipal principal, Duration ttl) {
        Instant expiresAt = principal.getAttribute(OAuth2TokenIntrospectionClaimNames.EXP);
        if (expiresAt == null) {
            return ttl;
        }
        Duration untilTokenExpires = Duration.between(Instant.now(), expiresAt);
        if (untilTokenExpires.isNegative()) {
            return Duration.ZERO;
        }
        return untilTokenExpires.compareTo(ttl) < 0 ? untilTokenExpires : ttl;
    }
}
