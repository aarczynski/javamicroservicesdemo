package pl.lunasoftware.demo.microservices.joboffers.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.SpringOpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    static final String JOB_OFFERS_SEARCH_SCOPE = "SCOPE_job-offers.search";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/job-offers/**").hasAuthority(JOB_OFFERS_SEARCH_SCOPE)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.opaqueToken(withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    OpaqueTokenIntrospector opaqueTokenIntrospector(OAuth2ResourceServerProperties properties,
                                                    @Value("${job-offers.security.introspection-cache-ttl}") Duration cacheTtl,
                                                    @Value("${job-offers.security.sso-timeout}") Duration ssoTimeout,
                                                    MeterRegistry meterRegistry) {
        OAuth2ResourceServerProperties.Opaquetoken opaqueToken = properties.getOpaquetoken();
        OpaqueTokenIntrospector sso = new SpringOpaqueTokenIntrospector(opaqueToken.getIntrospectionUri(),
                ssoClient(opaqueToken, ssoTimeout));
        return new CachingOpaqueTokenIntrospector(sso, cacheTtl, meterRegistry);
    }

    /**
     * What {@code SpringOpaqueTokenIntrospector.withIntrospectionUri(...).build()} creates, plus a timeout: the default
     * has none, so with the SSO down a request waited for the operating system to give up on the connection.
     */
    private static RestTemplate ssoClient(OAuth2ResourceServerProperties.Opaquetoken opaqueToken, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate ssoClient = new RestTemplate(requestFactory);
        ssoClient.getInterceptors().add(new BasicAuthenticationInterceptor(opaqueToken.getClientId(), opaqueToken.getClientSecret()));
        return ssoClient;
    }

    /**
     * Without it Spring Security wraps every filter, authentication and authorization in a Micrometer observation on
     * each request — ~11% of app-job-offers CPU in a JFR profile at ~1500 rps, for timers nothing reads.
     */
    @Bean
    SecurityObservationSettings securityObservationSettings() {
        return SecurityObservationSettings.noObservations();
    }
}
