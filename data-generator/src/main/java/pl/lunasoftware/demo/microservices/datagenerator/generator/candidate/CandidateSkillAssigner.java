package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate;

import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel;
import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SkillGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CandidateSkillAssigner {

    static final int MIN_SKILLS_PER_CANDIDATE = 1;
    static final int MAX_SKILLS_PER_CANDIDATE = 5;

    public CandidateSkillAssignment[] assignSkillsToCandidates(Candidate[] candidates) {
        List<CandidateSkillAssignment> assignments = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int n = ThreadLocalRandom.current().nextInt(MIN_SKILLS_PER_CANDIDATE, MAX_SKILLS_PER_CANDIDATE + 1);
            Set<String> chosenSkills = new HashSet<>();
            while (chosenSkills.size() < n) {
                chosenSkills.add(SkillGenerator.SKILL_NAMES[ThreadLocalRandom.current().nextInt(SkillGenerator.SKILL_NAMES.length)]);
            }
            for (String skillName : chosenSkills) {
                assignments.add(new CandidateSkillAssignment(
                        UUID.randomUUID(),
                        candidate.id(),
                        skillName,
                        randomSeniorityLevel()
                ));
            }
        }
        return assignments.toArray(new CandidateSkillAssignment[0]);
    }

    private SeniorityLevel randomSeniorityLevel() {
        SeniorityLevel[] values = SeniorityLevel.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
