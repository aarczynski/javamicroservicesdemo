package pl.lunasoftware.demo.microservices.loadtest.reader

import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.HOST_PARAM

class CliParamProviderSpec extends Specification {

    def cleanup() {
        System.clearProperty(DATA_FILE_PARAM)
        System.clearProperty(HOST_PARAM)
    }

    def "should read test data file path"() {
        given:
        System.setProperty(DATA_FILE_PARAM, 'test.file')

        when:
        def actual = CliParamProvider.CLI_PARAM_PROVIDER.readDataFile()

        then:
        actual == 'test.file'
    }

    def "should read host"() {
        given:
        System.setProperty(HOST_PARAM, 'test:8080')

        when:
        def actual = CliParamProvider.CLI_PARAM_PROVIDER.readHost()

        then:
        actual == 'test:8080'
    }
}
