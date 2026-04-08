package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CandidatesSqlGenerator {

    private static final int CHUNK_SIZE = 5_000;

    public String generateCandidatesBatchSql(Candidate[] candidates) {
        String header = "INSERT INTO candidate(id, first_name, last_name, email, geo_lat, geo_lon, radius_km, years_of_experience, expected_salary, created_at, updated_at) VALUES\n";
        return chunked(IntStream.range(0, candidates.length)
                .mapToObj(i -> formatCandidateRow(candidates[i])), header);
    }

    public String generateCandidatePreferredEmploymentTypesBatchSql(Candidate[] candidates) {
        String header = "INSERT INTO candidate_preferred_employment_type(candidate_id, employment_type) VALUES\n";
        Stream<String> rows = IntStream.range(0, candidates.length)
                .boxed()
                .flatMap(i -> Arrays.stream(candidates[i].preferredEmploymentTypes())
                        .map(type -> formatEmploymentTypeRow(candidates[i].id(), type)));
        return chunked(rows, header);
    }

    public String generateCandidateSkillAssignmentsBatchSql(CandidateSkillAssignment[] assignments) {
        String header = "INSERT INTO candidate_skill(id, candidate_id, skill_name, seniority_level, created_at, updated_at) VALUES\n";
        return chunked(IntStream.range(0, assignments.length)
                .mapToObj(i -> formatSkillAssignmentRow(assignments[i])), header);
    }

    private String chunked(Stream<String> rows, String header) {
        String[] rowArray = rows.toArray(String[]::new);
        return IntStream.range(0, (rowArray.length + CHUNK_SIZE - 1) / CHUNK_SIZE)
                .mapToObj(chunk -> {
                    int from = chunk * CHUNK_SIZE;
                    int to = Math.min(from + CHUNK_SIZE, rowArray.length);
                    String values = IntStream.range(from, to)
                            .mapToObj(i -> rowArray[i])
                            .collect(Collectors.joining(",\n"));
                    return header + values + ";\n";
                })
                .collect(Collectors.joining("\n"));
    }

    private String formatCandidateRow(Candidate c) {
        return String.format("('%s', '%s', '%s', '%s', %s, %s, %s, %s, %s, NOW(), NOW())",
                c.id(),
                escapeSingleQuote(c.firstName()), escapeSingleQuote(c.lastName()),
                c.email(),
                c.geoLat(), c.geoLon(), c.radiusKm(),
                c.yearsOfExperience(),
                c.expectedSalary());
    }

    private String formatEmploymentTypeRow(UUID candidateId, EmploymentType type) {
        return String.format("('%s', '%s')", candidateId, type);
    }

    private String formatSkillAssignmentRow(CandidateSkillAssignment a) {
        return String.format("('%s', '%s', '%s', '%s', NOW(), NOW())",
                a.id(), a.candidateId(),
                escapeSingleQuote(a.skillName()), a.seniorityLevel());
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
