package pl.lunasoftware.demo.microservices.datagenerator.generator.skill

import spock.lang.Specification

class SkillGeneratorSpec extends Specification {

    private def gen = new SkillGenerator()

    def "should return a skill for every predefined skill name"() {
        when:
        def actual = gen.skills()

        then:
        actual.length == SkillGenerator.SKILL_NAMES.length
        actual.collect { it.name() }.toSet().containsAll(SkillGenerator.SKILL_NAMES)
    }

    def "should generate skills with unique ids"() {
        when:
        def actual = gen.skills()

        then:
        actual.collect { it.id() }.toSet().size() == actual.length
    }
}
