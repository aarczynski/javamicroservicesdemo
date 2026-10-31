package pl.lunasoftware.demo.microservices.candidates.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring Security is on the classpath only for the OAuth2 client that authenticates calls to app-job-offers.
 * The candidates API itself stays open, as it was before SSO.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientService authorizedClients,
                                                          @Value("${candidates.security.sso-timeout}") Duration ssoTimeout) {
        return new TracedAuthorizedClientManager(registrations, authorizedClients, ssoTokenClient(ssoTimeout));
    }

    /**
     * Spring Security's default token client with a timeout: the default has none, so with the SSO down a request
     * waited for the operating system to give up on the connection (137-270 s on the cluster).
     */
    private static RestClientClientCredentialsTokenResponseClient ssoTokenClient(Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        RestClientClientCredentialsTokenResponseClient tokenClient = new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        return tokenClient;
    }

    /**
     * Without it Spring Security wraps every filter, authentication and authorization in a Micrometer observation on
     * each request, for timers nothing reads (see the same bean in app-job-offers).
     */
    @Bean
    SecurityObservationSettings securityObservationSettings() {
        return SecurityObservationSettings.noObservations();
    }
}
