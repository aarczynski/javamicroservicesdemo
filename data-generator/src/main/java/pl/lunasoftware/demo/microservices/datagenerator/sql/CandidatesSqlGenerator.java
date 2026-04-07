package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;

public class CandidatesSqlGenerator {

    public String generateCandidatesBatchSql(Candidate[] candidates) {
        String sqlTemplate = """
                INSERT INTO candidate(id, first_name, last_name, email, geo_lat, geo_lon, radius_km, years_of_experience, expected_salary, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Candidate c : candidates) {
            sb
                    .append(String.format("('%s', '%s', '%s', '%s', %s, %s, %s, %s, %s, NOW(), NOW()),",
                            c.id(),
                            escapeSingleQuote(c.firstName()), escapeSingleQuote(c.lastName()),
                            c.email(),
                            c.geoLat(), c.geoLon(), c.radiusKm(),
                            c.yearsOfExperience(),
                            c.expectedSalary()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateCandidatePreferredEmploymentTypesBatchSql(Candidate[] candidates) {
        String sqlTemplate = """
                INSERT INTO candidate_preferred_employment_type(candidate_id, employment_type) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Candidate c : candidates) {
            for (EmploymentType type : c.preferredEmploymentTypes()) {
                sb
                        .append(String.format("('%s', '%s'),", c.id(), type))
                        .append(System.lineSeparator());
            }
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateCandidateSkillAssignmentsBatchSql(CandidateSkillAssignment[] assignments) {
        String sqlTemplate = """
                INSERT INTO candidate_skill(id, candidate_id, skill_name, seniority_level, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (CandidateSkillAssignment a : assignments) {
            sb
                    .append(String.format("('%s', '%s', '%s', '%s', NOW(), NOW()),",
                            a.id(), a.candidateId(),
                            escapeSingleQuote(a.skillName()), a.seniorityLevel()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
