package pl.lunasoftware.demo.microservices.datagenerator.sql

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel
import spock.lang.Specification

import static java.math.RoundingMode.HALF_UP

class CandidatesSqlGeneratorSpec extends Specification {

    private static final UUID CANDIDATE_ID_1 = UUID.fromString('11111111-1111-1111-1111-111111111111')
    private static final UUID CANDIDATE_ID_2 = UUID.fromString('22222222-2222-2222-2222-222222222222')
    private static final UUID SKILL_ASSIGNMENT_ID_1 = UUID.fromString('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')

    private CandidatesSqlGenerator generator = new CandidatesSqlGenerator()

    def "should return candidates insert sql"() {
        given:
        def candidate1 = new Candidate(
                CANDIDATE_ID_1, 'John', 'Doe', 'john.doe@example.com',
                52.2297, 21.0122, 50.0,
                BigDecimal.valueOf(8000.00).setScale(2, HALF_UP),
                [EmploymentType.B2B] as EmploymentType[]
        )
        def candidate2 = new Candidate(
                CANDIDATE_ID_2, 'Jane', 'Smith', 'jane.smith@example.com',
                51.1079, 17.0385, 30.0,
                BigDecimal.valueOf(6000.00).setScale(2, HALF_UP),
                [EmploymentType.EMPLOYMENT] as EmploymentType[]
        )
        def candidates = [candidate1, candidate2] as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual == """\
                  INSERT INTO candidate(id, first_name, last_name, email, geo_lat, geo_lon, radius_km, expected_salary, created_at, updated_at) VALUES
                  ('$CANDIDATE_ID_1', 'John', 'Doe', 'john.doe@example.com', 52.2297, 21.0122, 50.0, 8000.00, NOW(), NOW()),
                  ('$CANDIDATE_ID_2', 'Jane', 'Smith', 'jane.smith@example.com', 51.1079, 17.0385, 30.0, 6000.00, NOW(), NOW());
                  """.stripIndent()
    }

    def "should return candidate preferred employment types insert sql"() {
        given:
        def candidate1 = new Candidate(
                CANDIDATE_ID_1, 'John', 'Doe', 'john@example.com',
                0.0, 0.0, 50.0, BigDecimal.TEN,
                [EmploymentType.B2B, EmploymentType.EMPLOYMENT] as EmploymentType[]
        )
        def candidate2 = new Candidate(
                CANDIDATE_ID_2, 'Jane', 'Smith', 'jane@example.com',
                0.0, 0.0, 30.0, BigDecimal.TEN,
                [EmploymentType.MANDATE_CONTRACT] as EmploymentType[]
        )
        def candidates = [candidate1, candidate2] as Candidate[]

        when:
        def actual = generator.generateCandidatePreferredEmploymentTypesBatchSql(candidates)

        then:
        actual.startsWith("INSERT INTO candidate_preferred_employment_type(candidate_id, employment_type) VALUES\n")
        actual.contains("'$CANDIDATE_ID_1', 'B2B'")
        actual.contains("'$CANDIDATE_ID_1', 'EMPLOYMENT'")
        actual.contains("'$CANDIDATE_ID_2', 'MANDATE_CONTRACT'")
    }

    def "should return candidate skill assignments insert sql"() {
        given:
        def assignment = new CandidateSkillAssignment(
                SKILL_ASSIGNMENT_ID_1, CANDIDATE_ID_1, 'Java', SeniorityLevel.SENIOR
        )
        def assignments = [assignment] as CandidateSkillAssignment[]

        when:
        def actual = generator.generateCandidateSkillAssignmentsBatchSql(assignments)

        then:
        actual == """\
                  INSERT INTO candidate_skill(id, candidate_id, skill_name, seniority_level, created_at, updated_at) VALUES
                  ('$SKILL_ASSIGNMENT_ID_1', '$CANDIDATE_ID_1', 'Java', 'SENIOR', NOW(), NOW());
                  """.stripIndent()
    }

    def "should escape single quote in candidate first name and last name"() {
        given:
        def candidate = new Candidate(
                CANDIDATE_ID_1, "O'Connor", "Mac'Donald", 'oconnor@example.com',
                0.0, 0.0, 50.0, BigDecimal.TEN,
                [] as EmploymentType[]
        )
        def candidates = [candidate] as Candidate[]

        when:
        def actual = generator.generateCandidatesBatchSql(candidates)

        then:
        actual.contains("O''Connor")
        actual.contains("Mac''Donald")
    }
}
