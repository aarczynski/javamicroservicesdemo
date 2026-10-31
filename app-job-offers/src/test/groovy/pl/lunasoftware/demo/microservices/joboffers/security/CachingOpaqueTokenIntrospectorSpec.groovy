package pl.lunasoftware.demo.microservices.joboffers.security

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CachingOpaqueTokenIntrospectorSpec extends Specification {

    private static final Duration TTL = Duration.ofSeconds(60)

    private OpaqueTokenIntrospector sso = Mock()

    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry()

    private CachingOpaqueTokenIntrospector introspector = new CachingOpaqueTokenIntrospector(sso, TTL, meterRegistry)

    def "should ask the SSO once and reuse its answer for the same active token"() {
        given:
        def principal = principalExpiringAt(Instant.now().plus(Duration.ofHours(1)))

        when:
        def first = introspector.introspect('token-a')
        def second = introspector.introspect('token-a')

        then:
        1 * sso.introspect('token-a') >> principal
        first.is(principal)
        second.is(principal)
    }

    def "should ask the SSO separately for each token"() {
        when:
        introspector.introspect('token-a')
        introspector.introspect('token-b')

        then:
        1 * sso.introspect('token-a') >> principalExpiringAt(Instant.now().plus(Duration.ofHours(1)))
        1 * sso.introspect('token-b') >> principalExpiringAt(Instant.now().plus(Duration.ofHours(1)))
    }

    def "should ask the SSO again for a token it rejected"() {
        when:
        introspector.introspect('revoked')

        then:
        1 * sso.introspect('revoked') >> { throw new BadOpaqueTokenException('Provided token isn\'t active') }
        thrown(BadOpaqueTokenException)

        when:
        introspector.introspect('revoked')

        then:
        1 * sso.introspect('revoked') >> { throw new BadOpaqueTokenException('Provided token isn\'t active') }
        thrown(BadOpaqueTokenException)
    }

    def "should not reuse the answer for a token that has already expired"() {
        given:
        def ssoCalls = 0
        sso.introspect('expired') >> {
            ssoCalls++
            principalExpiringAt(Instant.now().minusSeconds(1))
        }

        when:
        introspector.introspect('expired')

        then: 'the entry gets its expiry once the call completes, a moment after the answer is returned'
        new PollingConditions(timeout: 1).eventually {
            introspector.introspect('expired')
            assert ssoCalls >= 2
        }
    }

    def "should make requests waiting for the same token share the failure of one call to the SSO"() {
        given:
        def ssoAsked = new CountDownLatch(1)
        def ssoFails = new CountDownLatch(1)
        def requests = Executors.newFixedThreadPool(2)

        when:
        def first = requests.submit({ introspector.introspect('token-a') } as Callable)
        ssoAsked.await(5, TimeUnit.SECONDS)
        def second = requests.submit({ introspector.introspect('token-a') } as Callable)
        Thread.sleep(100)
        ssoFails.countDown()
        def failures = [first, second].collect { request -> causeOf { request.get(5, TimeUnit.SECONDS) } }

        then:
        1 * sso.introspect('token-a') >> {
            ssoAsked.countDown()
            ssoFails.await(5, TimeUnit.SECONDS)
            throw new OAuth2IntrospectionException('SSO unreachable')
        }
        failures.every { it instanceof OAuth2IntrospectionException }

        cleanup:
        requests.shutdownNow()
    }

    private static Throwable causeOf(Closure request) {
        try {
            request()
            return null
        } catch (ExecutionException e) {
            return e.cause
        }
    }

    def "should ask the caller to keep its trace only when it asked the SSO"() {
        given:
        sso.introspect('token-a') >> principalExpiringAt(Instant.now().plus(Duration.ofHours(1)))

        when:
        def missResponse = introspectInRequest('token-a')
        def hitResponse = introspectInRequest('token-a')

        then:
        missResponse.getHeader(CachingOpaqueTokenIntrospector.KEEP_TRACE_HEADER) == 'true'
        hitResponse.getHeader(CachingOpaqueTokenIntrospector.KEEP_TRACE_HEADER) == null
    }

    private MockHttpServletResponse introspectInRequest(String token) {
        def response = new MockHttpServletResponse()
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response))
        try {
            introspector.introspect(token)
            return response
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    def "should count cache hits and misses"() {
        given:
        sso.introspect('token-a') >> principalExpiringAt(Instant.now().plus(Duration.ofHours(1)))

        when:
        introspector.introspect('token-a')
        introspector.introspect('token-a')
        introspector.introspect('token-a')

        then:
        cacheGets('miss') == 1
        cacheGets('hit') == 2
    }

    private double cacheGets(String result) {
        meterRegistry.get('cache.gets')
                .tag('cache', CachingOpaqueTokenIntrospector.CACHE_NAME)
                .tag('result', result)
                .functionCounter()
                .count()
    }

    private static OAuth2AuthenticatedPrincipal principalExpiringAt(Instant expiresAt) {
        new OAuth2IntrospectionAuthenticatedPrincipal([sub: 'app-candidates', exp: expiresAt], [])
    }
}
