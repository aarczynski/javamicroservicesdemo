package pl.lunasoftware.demo.microservices.datagenerator.generator.company;

import com.github.javafaker.Faker;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CompanyGenerator {

    public static final int DEFAULT_COMPANIES = 10_000;

    private final Faker faker = new Faker();

    public Company[] randomCompanies(int count) {
        Set<Company> companies = new HashSet<>();
        while (companies.size() < count) {
            companies.add(randomCompany());
        }

        Company[] result = new Company[count];
        int i = 0;
        for (Company c : companies) {
            result[i] = c;
            i++;
        }
        return result;
    }

    private Company randomCompany() {
        return new Company(
                UUID.randomUUID(),
                faker.company().name(),
                ThreadLocalRandom.current().nextDouble(-90.0, 90.0),
                ThreadLocalRandom.current().nextDouble(-180.0, 180.0)
        );
    }
}
