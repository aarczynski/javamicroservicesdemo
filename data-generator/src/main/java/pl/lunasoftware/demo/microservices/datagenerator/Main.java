package pl.lunasoftware.demo.microservices.datagenerator;

import pl.lunasoftware.demo.microservices.datagenerator.generator.candidate.CandidateGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.CompanyGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.JobOfferGenerator;
import pl.lunasoftware.demo.microservices.datagenerator.writer.DataWriter;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        int candidates = CandidateGenerator.DEFAULT_CANDIDATES;
        int jobOffers = JobOfferGenerator.DEFAULT_JOB_OFFERS;
        int companies = CompanyGenerator.DEFAULT_COMPANIES;

        if (args.length >= 1) {
            candidates = parseArg(args[0], candidates, "candidates");
        }
        if (args.length >= 2) {
            jobOffers = parseArg(args[1], jobOffers, "job offers");
        }
        if (args.length >= 3) {
            companies = parseArg(args[2], companies, "companies");
        }

        System.out.printf("Generating %d companies, %d job offers, %d candidates.%n", companies, jobOffers, candidates);

        new DataWriter().writeRandomData(candidates, jobOffers, companies);
    }

    private static int parseArg(String arg, int defaultValue, String name) {
        try {
            return Integer.parseInt(arg);
        } catch (Exception e) {
            System.out.printf("Unable to parse %s count. Using default (%d).%n", name, defaultValue);
            return defaultValue;
        }
    }
}
