package pl.lunasoftware.demo.microservices.company.client

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class CompanyClientApplicationSpec extends Specification {

    def "should boot"() {
        expect:
        true
    }
}
