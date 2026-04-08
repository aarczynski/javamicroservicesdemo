package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOffer;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JobOffersSqlGenerator {

    private static final int CHUNK_SIZE = 5_000;

    public String generateCompaniesBatchSql(Company[] companies) {
        String header = "INSERT INTO company(id, name, geo_lat, geo_lon, created_at, updated_at) VALUES\n";
        return chunked(IntStream.range(0, companies.length)
                .mapToObj(i -> formatCompanyRow(companies[i])), header);
    }

    public String generateSkillsBatchSql(Skill[] skills) {
        String header = "INSERT INTO skill(id, name, created_at, updated_at) VALUES\n";
        return chunked(IntStream.range(0, skills.length)
                .mapToObj(i -> formatSkillRow(skills[i])), header);
    }

    public String generateJobOffersBatchSql(JobOffer[] jobOffers) {
        String header = "INSERT INTO job_offer(id, company_id, title, description, salary_from, salary_to, currency, required_years_of_experience, status, created_at, updated_at) VALUES\n";
        return chunked(IntStream.range(0, jobOffers.length)
                .mapToObj(i -> formatJobOfferRow(jobOffers[i])), header);
    }

    public String generateJobOfferEmploymentTypesBatchSql(JobOffer[] jobOffers) {
        String header = "INSERT INTO job_offer_employment_type(job_offer_id, employment_type) VALUES\n";
        Stream<String> rows = IntStream.range(0, jobOffers.length)
                .boxed()
                .flatMap(i -> Arrays.stream(jobOffers[i].employmentTypes())
                        .map(type -> formatEmploymentTypeRow(jobOffers[i].id(), type)));
        return chunked(rows, header);
    }

    public String generateJobOfferSkillAssignmentsBatchSql(JobOfferSkillAssignment[] assignments) {
        String header = "INSERT INTO job_offer_skill(id, job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at) VALUES\n";
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

    private String formatCompanyRow(Company c) {
        return String.format("('%s', '%s', %s, %s, NOW(), NOW())",
                c.id(), escapeSingleQuote(c.name()), c.geoLat(), c.geoLon());
    }

    private String formatSkillRow(Skill s) {
        return String.format("('%s', '%s', NOW(), NOW())",
                s.id(), escapeSingleQuote(s.name()));
    }

    private String formatJobOfferRow(JobOffer o) {
        return String.format("('%s', '%s', '%s', '%s', %s, %s, '%s', %s, '%s', NOW(), NOW())",
                o.id(), o.companyId(),
                escapeSingleQuote(o.title()), escapeSingleQuote(o.description()),
                o.salaryFrom(), o.salaryTo(),
                o.currency(), o.requiredYearsOfExperience(), o.status());
    }

    private String formatEmploymentTypeRow(UUID jobOfferId, EmploymentType type) {
        return String.format("('%s', '%s')", jobOfferId, type);
    }

    private String formatSkillAssignmentRow(JobOfferSkillAssignment a) {
        return String.format("('%s', '%s', '%s', '%s', %s, %s, NOW(), NOW())",
                a.id(), a.jobOfferId(), a.skillId(),
                a.requiredSeniorityLevel(), a.mandatory(), a.weight());
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
