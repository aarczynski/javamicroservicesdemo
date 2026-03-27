package pl.lunasoftware.demo.microservices.joboffers.company

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import spock.lang.Specification

@DataJpaTest
class CompanyRepositorySpec extends Specification {

    @Autowired
    private CompanyRepository companyRepository

    def "should find all companies"() {
        when:
        def companies = companyRepository.findAll()

        then:
        companies*.name as Set == ['TechCorp Poland sp. z o.o.', 'FinTech Kraków sp. z o.o.'] as Set
    }

    def "should find company by name"() {
        when:
        def company = companyRepository.findByName('TechCorp Poland sp. z o.o.')

        then:
        company.isPresent()
        company.get().name == 'TechCorp Poland sp. z o.o.'
    }

    def "should return empty when company not found"() {
        when:
        def company = companyRepository.findByName('Unknown Corp')

        then:
        company.isEmpty()
    }
}
