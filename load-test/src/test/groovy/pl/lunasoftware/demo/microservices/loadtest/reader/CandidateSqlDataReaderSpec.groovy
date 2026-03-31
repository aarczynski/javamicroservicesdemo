package pl.lunasoftware.demo.microservices.loadtest.reader

import spock.lang.Specification

import java.nio.file.Path

import static java.lang.System.clearProperty
import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM

class CandidateSqlDataReaderSpec extends Specification {

    private def reader = new CandidateSqlDataReader(Path.of(getClass().getClassLoader().getResource("test-data/test-candidates.sql").toURI()))

    void cleanup() {
        clearProperty(DATA_FILE_PARAM)
    }

    def "should read candidate id from default candidates data file"() {
        when:
        def actual = reader.readRandomCandidateId()

        then:
        actual == 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    }

    def "should read candidate id from parametrized candidates data file"() {
        given:
        System.setProperty(DATA_FILE_PARAM, getClass().getClassLoader().getResource("test-data/test-candidates.sql").getFile())

        when:
        def actual = reader.readRandomCandidateId()

        then:
        actual == 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    }
}
