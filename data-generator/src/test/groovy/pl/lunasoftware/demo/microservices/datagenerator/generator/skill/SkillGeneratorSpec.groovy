package pl.lunasoftware.demo.microservices.datagenerator.generator.skill

import spock.lang.Specification

class SkillGeneratorSpec extends Specification {

    private SkillGenerator gen = new SkillGenerator()

    def "should return a skill for every predefined skill name"() {
        when:
        def actual = gen.skills()

        then:
        actual.size() == SkillGenerator.SKILL_NAMES.length
        (actual*.name() as Set) == (SkillGenerator.SKILL_NAMES as Set)
    }

    def "should generate skills with unique ids"() {
        when:
        def actual = gen.skills()

        then:
        (actual*.id() as Set).size() == actual.size()
    }
}
