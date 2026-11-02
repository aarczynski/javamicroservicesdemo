package pl.lunasoftware.demo.microservices.candidates.security

import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TracedAuthorizedClientManagerSpec extends Specification {

    private static final String REGISTRATION_ID = 'job-offers'
    private static final int CONCURRENT_REQUESTS = 8

    private OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> sso = Mock()

    private ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId('app-candidates')
            .clientSecret('secret')
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri('http://sso/oauth2/token')
            .build()

    private InMemoryClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(registration)

    private TracedAuthorizedClientManager manager = new TracedAuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations), sso)

    def "should fetch a token once and reuse it while it is valid"() {
        when:
        def first = manager.authorize(authorizeRequest())
        def second = manager.authorize(authorizeRequest())

        then:
        1 * sso.getTokenResponse(_) >> tokenResponse('sso-token')
        first.accessToken.tokenValue == 'sso-token'
        second.accessToken.tokenValue == 'sso-token'
    }

    def "should make concurrent requests needing a token share one call to the SSO"() {
        given:
        def ssoAsked = new CountDownLatch(1)
        def ssoAnswers = new CountDownLatch(1)
        def requests = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)

        when:
        def first = requests.submit({ manager.authorize(authorizeRequest()) } as Callable)
        ssoAsked.await(5, TimeUnit.SECONDS)
        def others = (2..CONCURRENT_REQUESTS).collect { requests.submit({ manager.authorize(authorizeRequest()) } as Callable) }
        Thread.sleep(100)
        ssoAnswers.countDown()
        def tokens = ([first] + others).collect { request -> request.get(5, TimeUnit.SECONDS).accessToken.tokenValue }

        then:
        1 * sso.getTokenResponse(_) >> {
            ssoAsked.countDown()
            ssoAnswers.await(5, TimeUnit.SECONDS)
            tokenResponse('sso-token')
        }
        tokens.every { it == 'sso-token' }

        cleanup:
        requests.shutdownNow()
    }

    def "should make requests waiting for a token share the failure of one call to the SSO"() {
        given:
        def ssoAsked = new CountDownLatch(1)
        def ssoFails = new CountDownLatch(1)
        def requests = Executors.newFixedThreadPool(2)

        when:
        def first = requests.submit({ manager.authorize(authorizeRequest()) } as Callable)
        ssoAsked.await(5, TimeUnit.SECONDS)
        def second = requests.submit({ manager.authorize(authorizeRequest()) } as Callable)
        Thread.sleep(100)
        ssoFails.countDown()
        def failures = [first, second].collect { request -> causeOf { request.get(5, TimeUnit.SECONDS) } }

        then:
        1 * sso.getTokenResponse(_) >> {
            ssoAsked.countDown()
            ssoFails.await(5, TimeUnit.SECONDS)
            throw new OAuth2AuthorizationException(new OAuth2Error('server_error'))
        }
        failures.every { it instanceof OAuth2AuthorizationException }

        cleanup:
        requests.shutdownNow()
    }

    def "should ask the SSO again after a failed call"() {
        when:
        manager.authorize(authorizeRequest())

        then:
        1 * sso.getTokenResponse(_) >> { throw new OAuth2AuthorizationException(new OAuth2Error('server_error')) }
        thrown(OAuth2AuthorizationException)

        when:
        def client = manager.authorize(authorizeRequest())

        then:
        1 * sso.getTokenResponse(_) >> tokenResponse('sso-token')
        client.accessToken.tokenValue == 'sso-token'
    }

    private static Throwable causeOf(Closure request) {
        try {
            request()
            return null
        } catch (ExecutionException e) {
            return e.cause
        }
    }

    private static OAuth2AuthorizeRequest authorizeRequest() {
        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID).principal('app-candidates').build()
    }

    private static OAuth2AccessTokenResponse tokenResponse(String token) {
        OAuth2AccessTokenResponse.withToken(token)
                .tokenType(OAuth2AccessToken.TokenType.BEARER)
                .expiresIn(3600)
                .build()
    }
}
