package pl.lunasoftware.demo.microservices.datagenerator.sql;

import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOffer;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill;

public class JobOffersSqlGenerator {

    public String generateCompaniesBatchSql(Company[] companies) {
        String sqlTemplate = """
                INSERT INTO company(id, name, geo_lat, geo_lon, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Company c : companies) {
            sb
                    .append(String.format("('%s', '%s', %s, %s, NOW(), NOW()),", c.id(), escapeSingleQuote(c.name()), c.geoLat(), c.geoLon()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateSkillsBatchSql(Skill[] skills) {
        String sqlTemplate = """
                INSERT INTO skill(id, name, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (Skill s : skills) {
            sb
                    .append(String.format("('%s', '%s', NOW(), NOW()),", s.id(), escapeSingleQuote(s.name())))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateJobOffersBatchSql(JobOffer[] jobOffers) {
        String sqlTemplate = """
                INSERT INTO job_offer(id, company_id, title, description, salary_from, salary_to, currency, status, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (JobOffer o : jobOffers) {
            sb
                    .append(String.format("('%s', '%s', '%s', '%s', %s, %s, '%s', '%s', NOW(), NOW()),",
                            o.id(), o.companyId(),
                            escapeSingleQuote(o.title()), escapeSingleQuote(o.description()),
                            o.salaryFrom(), o.salaryTo(),
                            o.currency(), o.status()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateJobOfferEmploymentTypesBatchSql(JobOffer[] jobOffers) {
        String sqlTemplate = """
                INSERT INTO job_offer_employment_type(job_offer_id, employment_type) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (JobOffer o : jobOffers) {
            for (EmploymentType type : o.employmentTypes()) {
                sb
                        .append(String.format("('%s', '%s'),", o.id(), type))
                        .append(System.lineSeparator());
            }
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    public String generateJobOfferSkillAssignmentsBatchSql(JobOfferSkillAssignment[] assignments) {
        String sqlTemplate = """
                INSERT INTO job_offer_skill(id, job_offer_id, skill_id, required_seniority_level, mandatory, weight, created_at, updated_at) VALUES
                %s;
                """;

        StringBuilder sb = new StringBuilder();
        for (JobOfferSkillAssignment a : assignments) {
            sb
                    .append(String.format("('%s', '%s', '%s', '%s', %s, %s, NOW(), NOW()),",
                            a.id(), a.jobOfferId(), a.skillId(),
                            a.requiredSeniorityLevel(), a.mandatory(), a.weight()))
                    .append(System.lineSeparator());
        }
        sb.replace(sb.length() - 2, sb.length(), "");

        return sqlTemplate.formatted(sb.toString());
    }

    private String escapeSingleQuote(String s) {
        return s.replaceAll("'", "''");
    }
}
