package pl.lunasoftware.demo.microservices.loadtest.reader

import spock.lang.Specification

import java.time.Duration

import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.HOST_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.MAX_RPS_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.RAMPS_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.STEP_DURATION_PARAM

class CliParamProviderSpec extends Specification {

    def cleanup() {
        System.clearProperty(DATA_FILE_PARAM)
        System.clearProperty(HOST_PARAM)
        System.clearProperty(MAX_RPS_PARAM)
        System.clearProperty(STEP_DURATION_PARAM)
        System.clearProperty(RAMPS_PARAM)
    }

    def "should read test data file path"() {
        given:
        System.setProperty(DATA_FILE_PARAM, 'test.file')

        when:
        def actual = CliParamProvider.readDataFile()

        then:
        actual == 'test.file'
    }

    def "should read host"() {
        given:
        System.setProperty(HOST_PARAM, 'test:8080')

        when:
        def actual = CliParamProvider.readHost()

        then:
        actual == 'test:8080'
    }

    def "should return default maxRps when not set"() {
        when:
        def actual = CliParamProvider.readMaxRps()

        then:
        actual == 100
    }

    def "should read maxRps"() {
        given:
        System.setProperty(MAX_RPS_PARAM, '10')

        when:
        def actual = CliParamProvider.readMaxRps()

        then:
        actual == 10
    }

    def "should return default stepDuration when not set"() {
        when:
        def actual = CliParamProvider.readStepDuration()

        then:
        actual == Duration.ofSeconds(60)
    }

    def "should read stepDuration in seconds"() {
        given:
        System.setProperty(STEP_DURATION_PARAM, '30s')

        when:
        def actual = CliParamProvider.readStepDuration()

        then:
        actual == Duration.ofSeconds(30)
    }

    def "should read stepDuration in minutes"() {
        given:
        System.setProperty(STEP_DURATION_PARAM, '5m')

        when:
        def actual = CliParamProvider.readStepDuration()

        then:
        actual == Duration.ofMinutes(5)
    }

    def "should read stepDuration in hours"() {
        given:
        System.setProperty(STEP_DURATION_PARAM, '2h')

        when:
        def actual = CliParamProvider.readStepDuration()

        then:
        actual == Duration.ofHours(2)
    }

    def "should throw on invalid stepDuration format"() {
        given:
        System.setProperty(STEP_DURATION_PARAM, '30')

        when:
        CliParamProvider.readStepDuration()

        then:
        thrown(IllegalArgumentException)
    }

    def "should return default ramps when not set"() {
        when:
        def actual = CliParamProvider.readRamps()

        then:
        actual == 5
    }

    def "should read ramps"() {
        given:
        System.setProperty(RAMPS_PARAM, '2')

        when:
        def actual = CliParamProvider.readRamps()

        then:
        actual == 2
    }
}
