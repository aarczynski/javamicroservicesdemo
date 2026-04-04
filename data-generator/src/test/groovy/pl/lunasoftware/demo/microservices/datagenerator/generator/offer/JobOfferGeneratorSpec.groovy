package pl.lunasoftware.demo.microservices.datagenerator.generator.offer

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company
import spock.lang.Specification

class JobOfferGeneratorSpec extends Specification {

    private def gen = new JobOfferGenerator()

    def "should generate random job offers"() {
        given:
        def companies = Instancio.ofList(Company).size(10).create().toArray(new Company[0])
        int count = 100

        when:
        def actual = gen.randomJobOffers(count, companies)

        then:
        actual.length == count
        actual.every { it.id() != null }
        actual.every { it.companyId() != null }
        actual.every { it.title() != null && !it.title().isEmpty() }
        actual.every { it.salaryFrom() != null && it.salaryFrom().scale() == 2 }
        actual.every { it.salaryTo() != null && it.salaryTo().scale() == 2 }
        actual.every { it.salaryTo() > it.salaryFrom() }
        actual.every { it.currency() in ['PLN', 'EUR', 'USD'] }
        actual.every { it.status() in JobOfferStatus.values() }
        actual.every { it.employmentTypes() != null && it.employmentTypes().length >= 1 }
    }

    def "should use only company ids from provided companies"() {
        given:
        def companies = Instancio.ofList(Company).size(5).create().toArray(new Company[0])
        def companyIds = companies.collect { it.id() }.toSet()

        when:
        def actual = gen.randomJobOffers(200, companies)

        then:
        actual.every { companyIds.contains(it.companyId()) }
    }
}
