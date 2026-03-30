package pl.lunasoftware.demo.microservices.candidates.joboffer;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobOffersClientConfig {

    @Bean
    public ErrorDecoder jobOffersErrorDecoder() {
        return new JobOffersErrorDecoder();
    }
}
