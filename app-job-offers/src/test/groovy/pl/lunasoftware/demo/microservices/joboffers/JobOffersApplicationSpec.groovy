package pl.lunasoftware.demo.microservices.joboffers

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class JobOffersApplicationSpec extends Specification {

    def "should boot"() {
        expect:
        true
    }
}
