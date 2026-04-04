package pl.lunasoftware.demo.microservices.datagenerator.generator.offer

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill
import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssigner.MAX_SKILLS_PER_JOB_OFFER
import static pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssigner.MIN_SKILLS_PER_JOB_OFFER

class JobOfferSkillAssignerSpec extends Specification {

    private def assigner = new JobOfferSkillAssigner()

    def "should assign skills to each job offer"() {
        given:
        def jobOffers = Instancio.ofList(JobOffer).size(5).create().toArray(new JobOffer[0])
        def skills = Instancio.ofList(Skill).size(10).create().toArray(new Skill[0])

        when:
        def actual = assigner.assignSkillsToJobOffers(jobOffers, skills)

        then:
        actual.length >= jobOffers.length * MIN_SKILLS_PER_JOB_OFFER
        actual.length <= jobOffers.length * MAX_SKILLS_PER_JOB_OFFER
        actual.every { it.id() != null }
        actual.every { it.requiredSeniorityLevel() in SeniorityLevel.values() }
        actual.every { it.weight() != null && it.weight().scale() == 2 }
    }

    def "should not assign the same skill twice to one job offer"() {
        given:
        def jobOffers = Instancio.ofList(JobOffer).size(20).create().toArray(new JobOffer[0])
        def skills = Instancio.ofList(Skill).size(10).create().toArray(new Skill[0])

        when:
        def actual = assigner.assignSkillsToJobOffers(jobOffers, skills)

        then:
        actual.groupBy { it.jobOfferId() }.every { jobOfferId, assignments ->
            assignments.collect { it.skillId() }.toSet().size() == assignments.size()
        }
    }
}
