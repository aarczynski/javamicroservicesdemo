package pl.lunasoftware.demo.microservices.joboffers.skill

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import pl.lunasoftware.demo.microservices.joboffers.skill.SkillRepository
import spock.lang.Specification

@DataJpaTest
class SkillRepositorySpec extends Specification {

    @Autowired
    private SkillRepository skillRepository

    def "should find all skills"() {
        when:
        def skills = skillRepository.findAll()

        then:
        skills*.name as Set == ['Java', 'Spring Boot', 'React'] as Set
    }

    def "should find skills by names"() {
        when:
        def skills = skillRepository.findByNameIn(['Java', 'React'])

        then:
        skills*.name as Set == ['Java', 'React'] as Set
    }
}
