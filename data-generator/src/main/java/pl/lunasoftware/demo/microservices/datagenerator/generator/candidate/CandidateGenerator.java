package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate;

import com.github.javafaker.Faker;
import pl.lunasoftware.demo.microservices.datagenerator.generator.offer.EmploymentType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CandidateGenerator {

    public static final int DEFAULT_CANDIDATES = 100_000;

    private final Faker faker = new Faker();

    public Candidate[] randomCandidates(int count) {
        Candidate[] result = new Candidate[count];
        for (int i = 0; i < count; i++) {
            result[i] = randomCandidate();
        }
        return result;
    }

    private Candidate randomCandidate() {
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String emailBeginning = String.join(".", firstName, lastName, UUID.randomUUID().toString()).toLowerCase().replaceAll("'", "");

        return new Candidate(
                UUID.randomUUID(),
                firstName,
                lastName,
                faker.internet().emailAddress(emailBeginning),
                ThreadLocalRandom.current().nextDouble(-90.0, 90.0),
                ThreadLocalRandom.current().nextDouble(-180.0, 180.0),
                ThreadLocalRandom.current().nextDouble(10.0, 200.0),
                BigDecimal.valueOf(faker.number().randomDouble(2, 3_000, 25_000)).setScale(2, RoundingMode.HALF_UP),
                randomPreferredEmploymentTypes()
        );
    }

    private EmploymentType[] randomPreferredEmploymentTypes() {
        EmploymentType[] all = EmploymentType.values();
        int count = ThreadLocalRandom.current().nextInt(1, all.length + 1);
        Set<EmploymentType> chosen = new HashSet<>();
        while (chosen.size() < count) {
            chosen.add(all[ThreadLocalRandom.current().nextInt(all.length)]);
        }
        return chosen.toArray(new EmploymentType[0]);
    }
}
