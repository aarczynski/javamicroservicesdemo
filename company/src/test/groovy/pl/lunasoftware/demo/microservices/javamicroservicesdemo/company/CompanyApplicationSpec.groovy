package pl.lunasoftware.demo.microservices.javamicroservicesdemo.company

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class CompanyApplicationSpec extends Specification {

    def "should boot"() {
        expect:
        true
    }
}
