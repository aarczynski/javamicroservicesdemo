package pl.lunasoftware.demo.microservices.loadtest.profile

import spock.lang.Specification

import java.time.Duration

class RampProfileSpec extends Specification {

    def "should cap ramp duration at 1 minute when step is longer"() {
        when:
        def actual = RampProfile.rampDuration(Duration.ofMinutes(3))

        then:
        actual == Duration.ofMinutes(1)
    }

    def "should hold for the remainder of the step when step is longer than 1 minute"() {
        when:
        def actual = RampProfile.holdDuration(Duration.ofMinutes(3))

        then:
        actual == Duration.ofMinutes(2)
    }

    def "should use the whole step as ramp with no hold when step is exactly 1 minute"() {
        when:
        def ramp = RampProfile.rampDuration(Duration.ofMinutes(1))
        def hold = RampProfile.holdDuration(Duration.ofMinutes(1))

        then:
        ramp == Duration.ofMinutes(1)
        hold == Duration.ZERO
    }

    def "should use the whole step as ramp with no hold when step is shorter than 1 minute"() {
        when:
        def ramp = RampProfile.rampDuration(Duration.ofSeconds(30))
        def hold = RampProfile.holdDuration(Duration.ofSeconds(30))

        then:
        ramp == Duration.ofSeconds(30)
        hold == Duration.ZERO
    }
}
