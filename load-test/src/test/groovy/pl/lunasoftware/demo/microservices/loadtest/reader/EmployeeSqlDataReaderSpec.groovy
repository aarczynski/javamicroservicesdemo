package pl.lunasoftware.demo.microservices.loadtest.reader


import spock.lang.Specification

import java.nio.file.Path

import static java.lang.System.clearProperty
import static pl.lunasoftware.demo.microservices.loadtest.reader.SqlDataReader.DATA_FILE_PARAM

class EmployeeSqlDataReaderSpec extends Specification {

    private def reader = new EmployeeSqlDataReader(Path.of(getClass().getClassLoader().getResource("test-data/test-employees.sql").toURI()))


    void cleanup() {
        clearProperty(DATA_FILE_PARAM)
    }

    def "should read email from default employees data file"() {
        when:
        def actual = reader.readRandomEmployeeEmail()

        then:
        actual == 'brendan.schuppe@gmail.com'
    }

    def "should read email from parametrized employees data file"() {
        given:
        System.setProperty(DATA_FILE_PARAM, getClass().getClassLoader().getResource("test-data/test-employees.sql").getFile())

        when:
        def actual = reader.readRandomEmployeeEmail()

        then:
        actual == 'brendan.schuppe@gmail.com'
    }
}
