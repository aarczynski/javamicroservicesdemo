package pl.lunasoftware.demo.microservices.datagenerator.writer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOffer;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill;
import pl.lunasoftware.demo.microservices.datagenerator.sql.CandidatesSqlGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.sql.JobOffersSqlGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SqlFileWriter {

    private final JobOffersSqlGenerator jobOffersGenerator = new JobOffersSqlGenerator();
    private final CandidatesSqlGenerator candidatesGenerator = new CandidatesSqlGenerator();

    public void writeCompaniesToFile(Company[] companies, Path path) throws IOException {
        append(jobOffersGenerator.generateCompaniesBatchSql(companies), path);
    }

    public void writeSkillsToFile(Skill[] skills, Path path) throws IOException {
        append(jobOffersGenerator.generateSkillsBatchSql(skills), path);
    }

    public void writeJobOffersToFile(JobOffer[] jobOffers, Path path) throws IOException {
        append(jobOffersGenerator.generateJobOffersBatchSql(jobOffers), path);
    }

    public void writeJobOfferEmploymentTypesToFile(JobOffer[] jobOffers, Path path) throws IOException {
        append(jobOffersGenerator.generateJobOfferEmploymentTypesBatchSql(jobOffers), path);
    }

    public void writeJobOfferSkillAssignmentsToFile(JobOfferSkillAssignment[] assignments, Path path) throws IOException {
        append(jobOffersGenerator.generateJobOfferSkillAssignmentsBatchSql(assignments), path);
    }

    public void writeCandidatesToFile(Candidate[] candidates, Path path) throws IOException {
        append(candidatesGenerator.generateCandidatesBatchSql(candidates), path);
    }

    public void writeCandidatePreferredEmploymentTypesToFile(Candidate[] candidates, Path path) throws IOException {
        append(candidatesGenerator.generateCandidatePreferredEmploymentTypesBatchSql(candidates), path);
    }

    public void writeCandidateSkillAssignmentsToFile(CandidateSkillAssignment[] assignments, Path path) throws IOException {
        append(candidatesGenerator.generateCandidateSkillAssignmentsBatchSql(assignments), path);
    }

    private void append(String sql, Path path) throws IOException {
        Files.write(path, sql.getBytes(), StandardOpenOption.APPEND);
    }
}
