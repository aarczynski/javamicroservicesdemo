package pl.lunasoftware.demo.microservices.candidates

import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class CandidatesApplicationSpec extends Specification {

    def "should boot"() {
        expect:
        true
    }
}
