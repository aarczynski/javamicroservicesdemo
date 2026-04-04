package pl.lunasoftware.demo.microservices.loadtest.reader

import spock.lang.Specification

import java.nio.file.Path

import static pl.lunasoftware.demo.microservices.loadtest.reader.CliParamProvider.DATA_FILE_PARAM

class CandidateSqlDataReaderSpec extends Specification {

    private def reader = new CandidateSqlDataReader(
            Path.of(getClass().classLoader.getResource('test-data/test-candidates.sql').toURI())
    )

    def cleanup() {
        System.clearProperty(DATA_FILE_PARAM)
    }

    def "should read candidate id from default candidates data file"() {
        when:
        def actual = reader.readRandomCandidateId()

        then:
        actual == 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    }

    def "should read candidate id from parametrized candidates data file"() {
        given:
        System.setProperty(DATA_FILE_PARAM, getClass().classLoader.getResource('test-data/test-candidates.sql').file)

        when:
        def actual = reader.readRandomCandidateId()

        then:
        actual == 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    }

    def "should read candidate id from file with trailing newline"() {
        given:
        def readerWithTrailingNewline = new CandidateSqlDataReader(
                Path.of(getClass().classLoader.getResource('test-data/test-candidates-trailing-newline.sql').toURI())
        )

        when:
        def actual = readerWithTrailingNewline.readRandomCandidateId()

        then:
        actual == 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
    }
}
