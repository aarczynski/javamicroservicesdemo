package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate

import org.instancio.Instancio
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SkillGenerator
import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssigner.MAX_SKILLS_PER_CANDIDATE
import static pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssigner.MIN_SKILLS_PER_CANDIDATE

class CandidateSkillAssignerSpec extends Specification {

    private CandidateSkillAssigner assigner = new CandidateSkillAssigner()

    def "should assign skills to each candidate"() {
        given:
        def candidates = Instancio.ofList(Candidate).size(5).create().toArray() as Candidate[]

        when:
        def actual = assigner.assignSkillsToCandidates(candidates)

        then:
        actual.length >= candidates.length * MIN_SKILLS_PER_CANDIDATE
        actual.length <= candidates.length * MAX_SKILLS_PER_CANDIDATE
        actual.every { it.id() != null }
        actual.every { it.skillName() in SkillGenerator.SKILL_NAMES }
        actual.every { it.seniorityLevel() in SeniorityLevel.values() }
    }

    def "should not assign the same skill twice to one candidate"() {
        given:
        def candidates = Instancio.ofList(Candidate).size(20).create().toArray() as Candidate[]

        when:
        def actual = assigner.assignSkillsToCandidates(candidates)

        then:
        actual.groupBy { it.candidateId() }.every { _, assignments ->
            (assignments*.skillName() as Set).size() == assignments.size()
        }
    }
}
