package pl.lunasoftware.demo.microservices.candidates.security;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The manager Spring Cloud OpenFeign would create for its token interceptor, with each token lookup as a span:
 * {@code token.source=cache} while the stored token is still valid, {@code sso} when a new one is fetched (the call to
 * the SSO is then under it), {@code sso-pending} when another request is already fetching it. Requests that find the
 * token expiring wait for that one call instead of each fetching their own: Spring's manager doesn't share it, and at
 * 2500 rps one expiring token became ~560 calls to Hydra, ~560 new tokens for app-job-offers to introspect, and 503s
 * from the Gateway while they queued.
 */
class TracedAuthorizedClientManager implements OAuth2AuthorizedClientManager {

    private static final AttributeKey<String> TOKEN_SOURCE = AttributeKey.stringKey("token.source");

    private final AuthorizedClientServiceOAuth2AuthorizedClientManager delegate;
    private final OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> sso;
    private final ConcurrentMap<String, CompletableFuture<OAuth2AccessTokenResponse>> pendingSsoCalls = new ConcurrentHashMap<>();

    TracedAuthorizedClientManager(ClientRegistrationRepository registrations,
                                  OAuth2AuthorizedClientService authorizedClients,
                                  OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> sso) {
        this.sso = sso;
        this.delegate = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        this.delegate.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(clientCredentials -> clientCredentials.accessTokenResponseClient(this::fetchFromSso))
                .build());
    }

    @Override
    @WithSpan
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request) {
        Span.current().setAttribute(TOKEN_SOURCE, "cache");
        return delegate.authorize(request);
    }

    private OAuth2AccessTokenResponse fetchFromSso(OAuth2ClientCredentialsGrantRequest request) {
        String registrationId = request.getClientRegistration().getRegistrationId();
        CompletableFuture<OAuth2AccessTokenResponse> ssoCall = new CompletableFuture<>();
        CompletableFuture<OAuth2AccessTokenResponse> pendingSsoCall = pendingSsoCalls.putIfAbsent(registrationId, ssoCall);
        if (pendingSsoCall != null) {
            Span.current().setAttribute(TOKEN_SOURCE, "sso-pending");
            return join(pendingSsoCall);
        }
        Span.current().setAttribute(TOKEN_SOURCE, "sso");
        try {
            OAuth2AccessTokenResponse response = sso.getTokenResponse(request);
            ssoCall.complete(response);
            return response;
        } catch (RuntimeException e) {
            ssoCall.completeExceptionally(e);
            throw e;
        } finally {
            pendingSsoCalls.remove(registrationId, ssoCall);
        }
    }

    private static OAuth2AccessTokenResponse join(CompletableFuture<OAuth2AccessTokenResponse> ssoCall) {
        try {
            return ssoCall.join();
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
}
