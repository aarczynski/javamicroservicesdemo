package pl.lunasoftware.demo.microservices.loadtest.reader

import spock.lang.Specification

import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.HOST_PARAM

class CliParamProviderSpec extends Specification {

    void cleanup() {
        System.clearProperty(DATA_FILE_PARAM)
    }

    def "shouldReadTestDataFilePath"() {
        given:
        System.setProperty(DATA_FILE_PARAM, 'test.file')

        when:
        def actual = CliParamProvider.CLI_PARAM_PROVIDER.readDataFile()

        then:
        actual == 'test.file'
    }

    def "shouldReadHost"() {
        given:
        System.setProperty(HOST_PARAM, 'test:8080')

        when:
        def actual = CliParamProvider.CLI_PARAM_PROVIDER.readHost()

        then:
        actual == 'test:8080'
    }
}
