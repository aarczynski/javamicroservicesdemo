package pl.lunasoftware.demo.microservices.joboffers.offer

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import pl.lunasoftware.demo.microservices.joboffers.skill.SeniorityLevel
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillRepository
import spock.lang.Specification

@DataJpaTest
class JobOfferSkillRepositorySpec extends Specification {

    @Autowired
    private JobOfferSkillRepository jobOfferSkillRepository

    @Autowired
    private JobOfferRepository jobOfferRepository

    @Autowired
    private SkillRepository skillRepository

    def "should find skills for job offer"() {
        given:
        def offer = jobOfferRepository.findByStatus(JobOfferStatus.ACTIVE)
                .find { it.title == 'Senior Java Developer' }

        when:
        def skills = jobOfferSkillRepository.findByJobOfferId(offer.id)

        then:
        skills.size() == 2
    }

    def "should find mandatory skills for job offer"() {
        given:
        def offer = jobOfferRepository.findByStatus(JobOfferStatus.ACTIVE)
                .find { it.title == 'Senior Java Developer' }

        when:
        def skills = jobOfferSkillRepository.findByJobOfferId(offer.id)
        def mandatorySkills = skills.findAll { it.mandatory }

        then:
        mandatorySkills.size() == 1
        mandatorySkills[0].requiredSeniorityLevel == SeniorityLevel.SENIOR
    }

    def "should find offers requiring a specific skill"() {
        given:
        def javaSkill = skillRepository.findByNameIgnoreCase('java').get()

        when:
        def offers = jobOfferSkillRepository.findBySkillId(javaSkill.id)

        then:
        offers.size() == 3
    }
}
