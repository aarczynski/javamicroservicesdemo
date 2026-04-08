package pl.lunasoftware.demo.microservices.datagenerator.generator.offer;

import com.github.javafaker.Faker;
import pl.lunasoftware.demo.microservices.datagenerator.generator.company.Company;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JobOfferGenerator {

    public static final int DEFAULT_JOB_OFFERS = 50_000;

    private static final String[] JOB_TITLES = {
            "Software Engineer", "Senior Software Engineer", "Lead Software Engineer", "Principal Software Engineer",
            "Backend Developer", "Senior Backend Developer", "Frontend Developer", "Senior Frontend Developer",
            "Full Stack Developer", "Senior Full Stack Developer", "Mobile Developer", "Senior Mobile Developer",
            "DevOps Engineer", "Senior DevOps Engineer", "Platform Engineer", "Site Reliability Engineer",
            "Data Engineer", "Senior Data Engineer", "Data Scientist", "Machine Learning Engineer",
            "Solutions Architect", "Software Architect", "Technical Lead", "Engineering Manager", "Agile Coach",
            "QA Engineer", "Senior QA Engineer", "Security Engineer", "Database Administrator"
    };

    private static final String[] CURRENCIES = { "PLN" };

    private final Faker faker = new Faker();

    public JobOffer[] randomJobOffers(int count, Company[] companies) {
        JobOffer[] result = new JobOffer[count];
        for (int i = 0; i < count; i++) {
            result[i] = randomJobOffer(companies);
        }
        return result;
    }

    private JobOffer randomJobOffer(Company[] companies) {
        Company company = companies[ThreadLocalRandom.current().nextInt(companies.length)];
        BigDecimal salaryFrom = BigDecimal.valueOf(faker.number().randomDouble(2, 5_000, 20_000)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal salaryTo = salaryFrom.add(BigDecimal.valueOf(faker.number().randomDouble(2, 1_000, 10_000)).setScale(2, RoundingMode.HALF_UP));

        return new JobOffer(
                UUID.randomUUID(),
                company.id(),
                randomJobTitle(),
                faker.lorem().paragraph(),
                salaryFrom,
                salaryTo,
                randomCurrency(),
                ThreadLocalRandom.current().nextInt(0, 21),
                randomJobOfferStatus(),
                randomEmploymentTypes()
        );
    }

    private String randomJobTitle() {
        return JOB_TITLES[ThreadLocalRandom.current().nextInt(JOB_TITLES.length)];
    }

    private String randomCurrency() {
        return CURRENCIES[ThreadLocalRandom.current().nextInt(CURRENCIES.length)];
    }

    private JobOfferStatus randomJobOfferStatus() {
        int roll = ThreadLocalRandom.current().nextInt(10);
        if (roll < 7) {
            return JobOfferStatus.ACTIVE;
        }
        if (roll < 9) {
            return JobOfferStatus.DRAFT;
        }
        return JobOfferStatus.CLOSED;
    }

    private EmploymentType[] randomEmploymentTypes() {
        EmploymentType[] all = EmploymentType.values();
        int count = ThreadLocalRandom.current().nextInt(1, all.length + 1);
        Set<EmploymentType> chosen = new HashSet<>();
        while (chosen.size() < count) {
            chosen.add(all[ThreadLocalRandom.current().nextInt(all.length)]);
        }
        return chosen.toArray(new EmploymentType[0]);
    }
}
