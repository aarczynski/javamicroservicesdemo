package pl.lunasoftware.demo.microservices.datagenerator.generator.offer;

import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.Skill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JobOfferSkillAssigner {

    static final int MIN_SKILLS_PER_JOB_OFFER = 1;
    static final int MAX_SKILLS_PER_JOB_OFFER = 5;

    public JobOfferSkillAssignment[] assignSkillsToJobOffers(JobOffer[] jobOffers, Skill[] skills) {
        List<JobOfferSkillAssignment> assignments = new ArrayList<>();
        for (JobOffer jobOffer : jobOffers) {
            int n = ThreadLocalRandom.current().nextInt(MIN_SKILLS_PER_JOB_OFFER, MAX_SKILLS_PER_JOB_OFFER + 1);
            Set<Integer> chosenIndices = new HashSet<>();
            while (chosenIndices.size() < n) {
                chosenIndices.add(ThreadLocalRandom.current().nextInt(skills.length));
            }
            for (int idx : chosenIndices) {
                assignments.add(new JobOfferSkillAssignment(
                        UUID.randomUUID(),
                        jobOffer.id(),
                        skills[idx].id(),
                        randomSeniorityLevel(),
                        ThreadLocalRandom.current().nextBoolean(),
                        randomWeight()
                ));
            }
        }
        return assignments.toArray(new JobOfferSkillAssignment[0]);
    }

    private SeniorityLevel randomSeniorityLevel() {
        SeniorityLevel[] values = SeniorityLevel.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private BigDecimal randomWeight() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(0.1, 1.0)).setScale(2, RoundingMode.HALF_UP);
    }
}
