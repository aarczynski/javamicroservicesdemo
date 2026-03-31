package pl.lunasoftware.demo.microservices.datagenerator.writer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.Candidate;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssigner;
import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company;
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.CompanyGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOffer;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssigner;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferSkillAssignment;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SkillGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DataWriter {

    private final CompanyGenerator companyGenerator = new CompanyGenerator();
    private final SkillGenerator skillGenerator = new SkillGenerator();
    private final JobOfferGenerator jobOfferGenerator = new JobOfferGenerator();
    private final JobOfferSkillAssigner jobOfferSkillAssigner = new JobOfferSkillAssigner();
    private final CandidateGenerator candidateGenerator = new CandidateGenerator();
    private final CandidateSkillAssigner candidateSkillAssigner = new CandidateSkillAssigner();

    private final SqlFileWriter sqlFileWriter = new SqlFileWriter();

    private static final int JOB_OFFERS_BATCH_SIZE = 10_000;
    private static final int CANDIDATES_BATCH_SIZE = 10_000;

    public void writeRandomData(int candidatesCount, int jobOffersCount, int companiesCount) throws IOException {
        writeJobOffersData(jobOffersCount, companiesCount);
        writeCandidatesData(candidatesCount);
    }

    private void writeJobOffersData(int jobOffersCount, int companiesCount) throws IOException {
        Path outputDir = createDir("data-generator/output/job-offers");

        Path skillsFile = safeCreateFile(outputDir.resolve("02-skills.sql"));
        Skill[] skills = skillGenerator.skills();
        sqlFileWriter.writeSkillsToFile(skills, skillsFile);
        System.out.println("Wrote " + skills.length + " skills");

        Path companiesFile = safeCreateFile(outputDir.resolve("01-companies.sql"));
        Company[] companies = companyGenerator.randomCompanies(companiesCount);
        sqlFileWriter.writeCompaniesToFile(companies, companiesFile);
        System.out.println("Wrote " + companies.length + " companies");

        Path jobOffersFile = safeCreateFile(outputDir.resolve("03-job-offers.sql"));
        Path jobOfferEmpTypesFile = safeCreateFile(outputDir.resolve("04-job-offer-employment-types.sql"));
        Path jobOfferSkillsFile = safeCreateFile(outputDir.resolve("05-job-offer-skills.sql"));

        int jobOffersGenerated = 0;
        int remaining = jobOffersCount;
        while (jobOffersGenerated < jobOffersCount) {
            JobOffer[] batch = jobOfferGenerator.randomJobOffers(Math.min(JOB_OFFERS_BATCH_SIZE, remaining), companies);
            sqlFileWriter.writeJobOffersToFile(batch, jobOffersFile);
            sqlFileWriter.writeJobOfferEmploymentTypesToFile(batch, jobOfferEmpTypesFile);
            JobOfferSkillAssignment[] skillAssignments = jobOfferSkillAssigner.assignSkillsToJobOffers(batch, skills);
            sqlFileWriter.writeJobOfferSkillAssignmentsToFile(skillAssignments, jobOfferSkillsFile);
            remaining -= batch.length;
            jobOffersGenerated += batch.length;
            System.out.printf("Job offers generating progress: %.2f%%%n", 100.0 * jobOffersGenerated / jobOffersCount);
        }
        System.out.println("Wrote " + jobOffersGenerated + " job offers");
    }

    private void writeCandidatesData(int candidatesCount) throws IOException {
        Path outputDir = createDir("data-generator/output/candidates");

        Path candidatesFile = safeCreateFile(outputDir.resolve("01-candidates.sql"));
        Path candidateEmpTypesFile = safeCreateFile(outputDir.resolve("02-candidate-preferred-employment-types.sql"));
        Path candidateSkillsFile = safeCreateFile(outputDir.resolve("03-candidate-skills.sql"));

        int candidatesGenerated = 0;
        int remaining = candidatesCount;
        while (candidatesGenerated < candidatesCount) {
            Candidate[] batch = candidateGenerator.randomCandidates(Math.min(CANDIDATES_BATCH_SIZE, remaining));
            sqlFileWriter.writeCandidatesToFile(batch, candidatesFile);
            sqlFileWriter.writeCandidatePreferredEmploymentTypesToFile(batch, candidateEmpTypesFile);
            CandidateSkillAssignment[] skillAssignments = candidateSkillAssigner.assignSkillsToCandidates(batch);
            sqlFileWriter.writeCandidateSkillAssignmentsToFile(skillAssignments, candidateSkillsFile);
            remaining -= batch.length;
            candidatesGenerated += batch.length;
            System.out.printf("Candidates generating progress: %.2f%%%n", 100.0 * candidatesGenerated / candidatesCount);
        }
        System.out.println("Wrote " + candidatesGenerated + " candidates");
    }

    private static Path createDir(String dir) throws IOException {
        Path outputDir = Paths.get(dir);
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private static Path safeCreateFile(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
        Files.createFile(path);
        return path;
    }
}
