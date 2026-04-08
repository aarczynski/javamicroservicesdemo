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

    def "should read candidate id from file with multiple chunks separated by blank lines"() {
        given:
        def chunkedReader = new CandidateSqlDataReader(
                Path.of(getClass().classLoader.getResource('test-data/test-candidates-chunked.sql').toURI())
        )
        def validIds = [
                'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
                'b2c3d4e5-f6a7-8901-bcde-f12345678901',
                'c3d4e5f6-a7b8-9012-cdef-123456789012'
        ] as Set

        when:
        def results = (1..50).collect { chunkedReader.readRandomCandidateId() } as Set

        then:
        results.every { validIds.contains(it) }

        cleanup:
        chunkedReader.close()
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
