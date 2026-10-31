package pl.lunasoftware.demo.microservices.candidates.joboffer

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import pl.lunasoftware.demo.microservices.candidates.candidate.EmploymentType
import pl.lunasoftware.demo.microservices.candidates.skill.SeniorityLevel
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest
class JobOffersClientSsoSpec extends Specification {

    private static final int SSO_PORT = 9998
    private static final int JOB_OFFERS_PORT = 9999

    @Shared
    private HttpServer sso = HttpServer.create(new InetSocketAddress(SSO_PORT), 0)

    @Shared
    private HttpServer jobOffers = HttpServer.create(new InetSocketAddress(JOB_OFFERS_PORT), 0)

    @Shared
    private AtomicInteger tokenRequests = new AtomicInteger()

    @Shared
    private AtomicReference<String> jobOffersAuthorization = new AtomicReference<>()

    @Autowired
    private JobOffersClient jobOffersClient

    def setupSpec() {
        sso.createContext('/oauth2/token') { exchange ->
            tokenRequests.incrementAndGet()
            respond(exchange, '{"access_token":"sso-token","token_type":"bearer","expires_in":3600,"scope":"job-offers.search"}')
        }
        jobOffers.createContext('/api/v1/job-offers/search') { exchange ->
            jobOffersAuthorization.set(exchange.requestHeaders.getFirst('Authorization'))
            respond(exchange, '[]')
        }
        sso.start()
        jobOffers.start()
    }

    def cleanupSpec() {
        sso.stop(0)
        jobOffers.stop(0)
    }

    def "should call job-offers with a bearer token from SSO and reuse it until it expires"() {
        when:
        jobOffersClient.searchOffers(searchRequest())
        jobOffersClient.searchOffers(searchRequest())

        then:
        jobOffersAuthorization.get() == 'Bearer sso-token'
        tokenRequests.get() == 1
    }

    private static JobOffersSearchRequest searchRequest() {
        new JobOffersSearchRequest(
                [new CandidateSkillDto('Java', SeniorityLevel.MID)] as Set,
                52.2297, 21.0122, 100,
                new BigDecimal('15000'),
                [EmploymentType.B2B] as Set,
                5, 40
        )
    }

    private static void respond(HttpExchange exchange, String json) {
        def body = json.getBytes('UTF-8')
        exchange.responseHeaders.add('Content-Type', 'application/json')
        exchange.sendResponseHeaders(200, body.length)
        exchange.responseBody.withCloseable { it.write(body) }
    }
}
