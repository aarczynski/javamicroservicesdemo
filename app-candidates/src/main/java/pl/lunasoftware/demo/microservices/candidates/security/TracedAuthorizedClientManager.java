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

/**
 * The manager Spring Cloud OpenFeign would create for its token interceptor, with each token lookup as a span:
 * {@code token.source=cache} while the stored token is still valid, {@code sso} when a new one is fetched (the call to
 * the SSO is then under it).
 */
class TracedAuthorizedClientManager implements OAuth2AuthorizedClientManager {

    private static final AttributeKey<String> TOKEN_SOURCE = AttributeKey.stringKey("token.source");

    private final AuthorizedClientServiceOAuth2AuthorizedClientManager delegate;

    TracedAuthorizedClientManager(ClientRegistrationRepository registrations,
                                  OAuth2AuthorizedClientService authorizedClients,
                                  OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> sso) {
        this.delegate = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        this.delegate.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(clientCredentials -> clientCredentials
                        .accessTokenResponseClient(request -> fetchFromSso(sso, request)))
                .build());
    }

    @Override
    @WithSpan
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizeRequest request) {
        Span.current().setAttribute(TOKEN_SOURCE, "cache");
        return delegate.authorize(request);
    }

    private static OAuth2AccessTokenResponse fetchFromSso(OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> sso,
                                                          OAuth2ClientCredentialsGrantRequest request) {
        Span.current().setAttribute(TOKEN_SOURCE, "sso");
        return sso.getTokenResponse(request);
    }
}
