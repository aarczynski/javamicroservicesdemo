package pl.lunasoftware.demo.microservices.datagenerator.sql

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel
import spock.lang.Specification

import java.math.RoundingMode

class CandidatesSqlGeneratorSpec extends Specification {

    private static final UUID CANDIDATE_ID_1 = UUID.fromString('11111111-1111-1111-1111-111111111111')
    private static final UUID CANDIDATE_ID_2 = UUID.fromString('22222222-2222-2222-2222-222222222222')
    private static final UUID SKILL_ASSIGNMENT_ID_1 = UUID.fromString('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')

    private def generator = new CandidatesSqlGenerator()

    def "should return candidates insert SQL"() {
        given:
        def candidate1 = new Candidate(CANDIDATE_ID_1, 'John', 'Doe', 'john.doe@example.com',
                52.2297, 21.0122, 50.0, 8,
                BigDecimal.valueOf(8000.00).setScale(2, RoundingMode.HALF_UP),
                [EmploymentType.B2B] as EmploymentType[])
        def candidate2 = new Candidate(CANDIDATE_ID_2, 'Jane', 'Smith', 'jane.smith@example.com',
                51.1079, 17.0385, 30.0, 3,
                BigDecimal.valueOf(6000.00).setScale(2, RoundingMode.HALF_UP),
                [EmploymentType.EMPLOYMENT] as EmploymentType[])
        def candidates = [candidate1, candidate2] as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual == """\
                |INSERT INTO candidate(id, first_name, last_name, email, geo_lat, geo_lon, radius_km, years_of_experience, expected_salary, created_at, updated_at) VALUES
                |('$CANDIDATE_ID_1', 'John', 'Doe', 'john.doe@example.com', 52.2297, 21.0122, 50.0, 8, 8000.00, NOW(), NOW()),
                |('$CANDIDATE_ID_2', 'Jane', 'Smith', 'jane.smith@example.com', 51.1079, 17.0385, 30.0, 3, 6000.00, NOW(), NOW());
                |""".stripMargin()
    }

    def "should return candidate preferred employment types insert SQL"() {
        given:
        def candidate1 = new Candidate(CANDIDATE_ID_1, 'John', 'Doe', 'john@example.com',
                0.0, 0.0, 50.0, 5, BigDecimal.TEN,
                [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as EmploymentType[])
        def candidate2 = new Candidate(CANDIDATE_ID_2, 'Jane', 'Smith', 'jane@example.com',
                0.0, 0.0, 30.0, 2, BigDecimal.TEN,
                [EmploymentType.MANDATE_CONTRACT] as EmploymentType[])
        def candidates = [candidate1, candidate2] as Candidate[]

        when:
        def actual = generator.generateCandidatePreferredEmploymentTypesBatchSql(candidates)

        then:
        actual.startsWith('INSERT INTO candidate_preferred_employment_type(candidate_id, employment_type) VALUES\n')
        actual.contains("'$CANDIDATE_ID_1', 'B2B'")
        actual.contains("'$CANDIDATE_ID_1', 'EMPLOYMENT'")
        actual.contains("'$CANDIDATE_ID_2', 'MANDATE_CONTRACT'")
    }

    def "should return candidate skill assignments insert SQL"() {
        given:
        def assignment = new CandidateSkillAssignment(SKILL_ASSIGNMENT_ID_1, CANDIDATE_ID_1, 'Java', SeniorityLevel.SENIOR)
        def assignments = [assignment] as CandidateSkillAssignment[]

        when:
        def actual = generator.generateCandidateSkillAssignmentsBatchSql(assignments)

        then:
        actual == """\
                |INSERT INTO candidate_skill(id, candidate_id, skill_name, seniority_level, created_at, updated_at) VALUES
                |('$SKILL_ASSIGNMENT_ID_1', '$CANDIDATE_ID_1', 'Java', 'SENIOR', NOW(), NOW());
                |""".stripMargin()
    }

    def "should produce multiple INSERT statements when row count exceeds chunk size"() {
        given:
        def candidates = (1..5001).collect { i ->
            new Candidate(UUID.randomUUID(), 'John', 'Doe', "user$i@example.com",
                    0.0, 0.0, 50.0, 5, BigDecimal.TEN, [] as EmploymentType[])
        } as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual.split('INSERT INTO candidate').length - 1 == 2
    }

    def "should include all rows across chunks"() {
        given:
        def emails = (1..5001).collect { "user$it@example.com" }
        def candidates = emails.collect { email ->
            new Candidate(UUID.randomUUID(), 'John', 'Doe', email,
                    0.0, 0.0, 50.0, 5, BigDecimal.TEN, [] as EmploymentType[])
        } as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        emails.every { email -> actual.contains(email) }
    }

    def "should terminate each chunk with a semicolon and not leave dangling commas between chunks"() {
        given:
        def candidates = (1..5001).collect { i ->
            new Candidate(UUID.randomUUID(), 'John', 'Doe', "user$i@example.com",
                    0.0, 0.0, 50.0, 5, BigDecimal.TEN, [] as EmploymentType[])
        } as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual.split('\n').count { it.endsWith(';') } == 2
        !actual.contains(',\n\nINSERT')
    }

    def "should escape single quote in candidate first name and last name"() {
        given:
        def candidate = new Candidate(CANDIDATE_ID_1, "O'Connor", "Mac'Donald", 'oconnor@example.com',
                0.0, 0.0, 50.0, 0, BigDecimal.TEN, [] as EmploymentType[])
        def candidates = [candidate] as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual.contains("O''Connor")
        actual.contains("Mac''Donald")
    }
}
