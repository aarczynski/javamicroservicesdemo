package pl.lunasoftware.demo.microservices.loadtest.reader


import spock.lang.Specification

import java.nio.file.Path

import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM


class DepartmentsSqlDataReaderSpec extends Specification {

    private def reader = new DepartmentsSqlDataReader(Path.of(getClass().getClassLoader().getResource("test-data/test-departments.sql").toURI()))

    void cleanup() {
        System.clearProperty(DATA_FILE_PARAM)
    }

    def "should read department name from default departments data file"() {
        when:
        def actual = reader.readRandomDepartmentName()

        then:
        actual == 'Spencer-Nicolas'
    }

    def "should read department name from parametrized departments data file"() {
        given:
        System.setProperty(DATA_FILE_PARAM, getClass().getClassLoader().getResource("test-data/test-departments.sql").getFile())

        when:
        def actual = reader.readRandomDepartmentName()

        then:
        actual == 'Spencer-Nicolas'
    }
}
