package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferRepository
import pl.lunasoftware.demo.microservices.joboffers.offer.JobOfferSkillRepository
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel
import spock.lang.Specification

@DataJpaTest
class JobOfferSkillRepositorySpec extends Specification {

    @Autowired
    private JobOfferSkillRepository jobOfferSkillRepository

    @Autowired
    private JobOfferRepository jobOfferRepository

    def "should find skills for job offer"() {
        given:
        def offer = jobOfferRepository.findAll().find { it.title == 'Senior Java Developer' }

        when:
        def skills = jobOfferSkillRepository.findByJobOfferId(offer.id)

        then:
        skills.size() == 2
    }

    def "should find mandatory skills for job offer"() {
        given:
        def offer = jobOfferRepository.findAll().find { it.title == 'Senior Java Developer' }

        when:
        def skills = jobOfferSkillRepository.findByJobOfferId(offer.id)
        def mandatorySkills = skills.findAll { it.mandatory }

        then:
        mandatorySkills.size() == 1
        mandatorySkills[0].requiredSeniorityLevel == SeniorityLevel.SENIOR
    }
}
