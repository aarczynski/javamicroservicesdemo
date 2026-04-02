package pl.lunasoftware.demo.microservices.datagenerator.generator.candidate;

import pl.lunasoftware.demo.microservices.datagenerator.generator.skill.SeniorityLevel;

import java.util.UUID;

public record CandidateSkillAssignment(
        UUID id,
        UUID candidateId,
        String skillName,
        SeniorityLevel seniorityLevel
) {}
