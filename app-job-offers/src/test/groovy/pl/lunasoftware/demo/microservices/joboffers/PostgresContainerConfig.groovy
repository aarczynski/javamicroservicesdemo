package pl.lunasoftware.demo.microservices.joboffers

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfig {

    private static final String POSTGRES_IMAGE = 'postgres:18.6'

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        new PostgreSQLContainer(POSTGRES_IMAGE)
    }
}
